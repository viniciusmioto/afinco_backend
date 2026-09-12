package com.afinco.backend.statement;

import com.afinco.backend.domain.TransactionType;
import com.afinco.backend.exception.StatementParsingException;
import com.afinco.backend.exception.UnsupportedStatementException;
import com.afinco.backend.statement.dto.ParsedTransactionDTO;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

/** English TD credit-card e-statements. Scans and deposit-account layouts are not supported. */
@Component
public class TDBankStatementParser implements StatementParser {
    private static final Pattern STATEMENT_DATE = Pattern.compile(
            "(?i)STATEMENT\\s*DATE\\s*:\\s*([a-z]+)\\.?\\s*(\\d{1,2})\\s*,?\\s*(\\d{4})");
    private static final String MONTH = "(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)";
    private static final Pattern ROW = Pattern.compile(
            "(?i)^" + MONTH + "\\.?\\s*(\\d{1,2})\\s+" + MONTH + "\\.?\\s*(\\d{1,2})\\s+(.+)$");
    private static final Pattern ROW_START = Pattern.compile("(?i)^" + MONTH + "\\.?\\s*\\d");
    private static final Pattern MONEY = Pattern.compile(
            "(?i)(?:^|\\s)(\\(?[+-]?\\s*\\$?\\s*[+-]?\\s*(?:\\d{1,3}(?:,\\d{3})+|\\d+)\\.\\d{2}\\)?\\s*(?:CR|DR|-)?)\\s*$");
    private final PdfTextExtractor extractor;

    public TDBankStatementParser(PdfTextExtractor extractor) {
        this.extractor = extractor;
    }

    @Override
    public String bankName() {
        return "TD Bank";
    }

    @Override
    public StatementType statementType() {
        return StatementType.CREDIT_CARD;
    }

    @Override
    public boolean supports(String documentText) {
        if (documentText == null) {
            return false;
        }
        String compact = compact(documentText);
        return compact.contains("STATEMENTDATE")
                && (compact.contains("TDCASHBACK") || compact.contains("TDVISA")
                || compact.contains("TDCANADATRUST") || compact.contains("TDAEROPLAN")
                || compact.contains("TDFIRSTCLASS") || compact.contains("TDREWARDS"));
    }

    @Override
    public List<ParsedTransactionDTO> parse(InputStream stream) {
        if (stream == null) {
            throw new StatementParsingException("A PDF stream is required");
        }
        try {
            byte[] bytes = stream.readNBytes(PdfTextExtractor.MAX_BYTES + 1);
            String text = extractor.extract(bytes);
            if (!supports(text)) {
                throw new UnsupportedStatementException("The document is not a recognized TD statement");
            }
            LocalDate statementDate = statementDate(text);
            String table = extractTable(bytes);
            return parseText("STATEMENT DATE: "
                    + statementDate.format(DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.ENGLISH))
                    + "\n" + table);
        } catch (IOException exception) {
            throw new StatementParsingException("The PDF cannot be read");
        }
    }

    List<ParsedTransactionDTO> parseText(String text) {
        LocalDate statementDate = statementDate(text);
        List<ParsedTransactionDTO> transactions = new ArrayList<>();
        PendingRow pending = null;
        boolean inTable = false;
        boolean foundTable = false;
        for (String original : text.split("\\R")) {
            String line = original.strip();
            String compact = compact(line);
            if (compact.contains("ACTIVITYDESCRIPTION") || compact.contains("TRANSACTIONDESCRIPTION")) {
                inTable = true;
                foundTable = true;
                continue;
            }
            if (!inTable || line.isEmpty()) {
                continue;
            }
            if (isFooter(compact)) {
                if (pending != null) {
                    transactions.add(pending.finish());
                    pending = null;
                }
                inTable = false;
                continue;
            }
            if (isHeader(compact)) {
                continue;
            }
            Matcher row = ROW.matcher(line);
            if (row.matches()) {
                if (pending != null) {
                    transactions.add(pending.finish());
                }
                LocalDate posting = resolveDate(row.group(3), row.group(4), statementDate);
                LocalDate date = resolveDate(row.group(1), row.group(2), posting);
                pending = new PendingRow(date);
                pending.append(row.group(5));
            } else if (ROW_START.matcher(line).find()) {
                throw new StatementParsingException("A transaction row has an unsupported date or layout");
            } else if (pending != null) {
                pending.append(line);
            }
        }
        if (pending != null) {
            transactions.add(pending.finish());
        }
        if (!foundTable || transactions.isEmpty()) {
            throw new StatementParsingException("No supported transaction rows were found");
        }
        return List.copyOf(transactions);
    }

    private boolean isHeader(String line) {
        return line.contains("STATEMENTDATE") || line.contains("PREVIOUSSTATEMENT")
                || line.startsWith("TRANSACTIONPOSTING") || line.startsWith("DATEDATE")
                || line.startsWith("PREVIOUS") || line.startsWith("CONTINUED")
                || line.matches("\\d+OF\\d+");
    }

    private boolean isFooter(String line) {
        return line.startsWith("TOTALNEWBALANCE") || line.startsWith("TOTALACCOUNTBALANCE")
                || line.startsWith("TOTALFOR") || line.startsWith("SUBTOTAL")
                || line.startsWith("TDMESSAGE") || line.startsWith("NEWBALANCE");
    }

    private LocalDate statementDate(String text) {
        if (text == null) {
            throw new StatementParsingException("Statement date is missing");
        }
        Matcher match = STATEMENT_DATE.matcher(text);
        if (!match.find()) {
            throw new StatementParsingException("Statement date is missing or unsupported");
        }
        try {
            return LocalDate.of(Integer.parseInt(match.group(3)), month(match.group(1)),
                    Integer.parseInt(match.group(2)));
        } catch (DateTimeException | IllegalArgumentException exception) {
            throw new StatementParsingException("Statement date is invalid");
        }
    }

    private LocalDate resolveDate(String monthText, String dayText, LocalDate anchor) {
        try {
            int month = month(monthText);
            int day = Integer.parseInt(dayText);
            int year = month > anchor.getMonthValue() ? anchor.getYear() - 1 : anchor.getYear();
            LocalDate date = LocalDate.of(year, month, day);
            // Credit-card rows belong to the current billing cycle (allow late posting).
            if (date.isAfter(anchor) || date.isBefore(anchor.minusMonths(2))) {
                throw new StatementParsingException("Transaction date is outside the supported billing cycle");
            }
            return date;
        } catch (DateTimeException | IllegalArgumentException exception) {
            throw new StatementParsingException("Transaction date is invalid");
        }
    }

    private int month(String text) {
        String abbreviated = text.substring(0, 3).toUpperCase(Locale.ROOT);
        for (Month month : Month.values()) {
            if (month.name().startsWith(abbreviated)) {
                return month.getValue();
            }
        }
        throw new StatementParsingException("Month is invalid");
    }

    private String compact(String text) {
        return text.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
    }

    private String extractTable(byte[] bytes) throws IOException {
        StringBuilder tables = new StringBuilder();
        try (PDDocument document = extractor.open(bytes)) {
            for (int index = 0; index < document.getNumberOfPages(); index++) {
                TableHeaderLocator locator = new TableHeaderLocator();
                locator.setStartPage(index + 1);
                locator.setEndPage(index + 1);
                locator.getText(document);
                Rectangle2D region = locator.tableRegion(document.getPage(index));
                if (region != null) {
                    PDFTextStripperByArea stripper = new PDFTextStripperByArea();
                    stripper.setSortByPosition(true);
                    stripper.addRegion("transactions", region);
                    stripper.extractRegions(document.getPage(index));
                    tables.append(stripper.getTextForRegion("transactions")).append('\n');
                }
            }
        }
        return tables.toString();
    }

    /** Locate the AMOUNT column in the table header, so the adjacent sidebar is excluded. */
    private static final class TableHeaderLocator extends PDFTextStripper {
        private final List<TextPosition> glyphs = new ArrayList<>();

        private TableHeaderLocator() throws IOException {
            super();
        }

        @Override
        protected void processTextPosition(TextPosition position) {
            glyphs.add(position);
        }

        private Rectangle2D tableRegion(PDPage page) {
            TreeMap<Integer, List<TextPosition>> lines = new TreeMap<>();
            for (TextPosition glyph : glyphs) {
                lines.computeIfAbsent(Math.round(glyph.getYDirAdj()), ignored -> new ArrayList<>()).add(glyph);
            }
            for (List<TextPosition> positions : lines.values()) {
                positions.sort(Comparator.comparing(TextPosition::getXDirAdj));
                StringBuilder text = new StringBuilder();
                List<TextPosition> characters = new ArrayList<>();
                for (TextPosition position : positions) {
                    for (char character : position.getUnicode().toCharArray()) {
                        if (!Character.isWhitespace(character)) {
                            text.append(Character.toUpperCase(character));
                            characters.add(position);
                        }
                    }
                }
                int amount = text.indexOf("AMOUNT");
                if (amount >= 0 && text.indexOf("DESCRIPTION") >= 0) {
                    TextPosition last = characters.get(amount + "AMOUNT".length() - 1);
                    float right = last.getXDirAdj() + last.getWidthDirAdj() + 12;
                    float top = positions.getFirst().getYDirAdj() - 24;
                    return new Rectangle2D.Float(0, Math.max(0, top), right,
                            page.getCropBox().getHeight() - Math.max(0, top));
                }
            }
            return null;
        }
    }

    private final class PendingRow {
        private final LocalDate date;
        private final StringBuilder description = new StringBuilder();
        private BigDecimal amount;

        private PendingRow(LocalDate date) {
            this.date = date;
        }

        private void append(String text) {
            Matcher money = MONEY.matcher(text);
            String detail = text;
            if (amount == null && money.find()) {
                String value = money.group(1).replaceAll("\\s+", "");
                amount = new BigDecimal(value.replaceAll("[^0-9.]", ""));
                detail = text.substring(0, money.start()).strip();
            }
            if (!detail.isBlank()) {
                if (!description.isEmpty()) {
                    description.append(' ');
                }
                description.append(detail.strip());
            }
        }

        private ParsedTransactionDTO finish() {
            if (amount == null || amount.signum() <= 0 || description.isEmpty()) {
                throw new StatementParsingException("A transaction row is incomplete or has an invalid amount");
            }
            return new ParsedTransactionDTO(
                    date, amount, TransactionType.CREDIT, description.toString(), bankName());
        }
    }
}
