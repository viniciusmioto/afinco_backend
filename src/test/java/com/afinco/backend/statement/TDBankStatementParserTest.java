package com.afinco.backend.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.afinco.backend.domain.TransactionType;
import com.afinco.backend.exception.StatementParsingException;
import com.afinco.backend.statement.dto.ParsedTransactionDTO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TDBankStatementParserTest {

    private static final String TABLE_HEADER = """
            TRANSACTION POSTING
            DATE DATE ACTIVITY DESCRIPTION AMOUNT($)
            """;

    private final TDBankStatementParser parser = new TDBankStatementParser(new PdfTextExtractor());

    @Test
    void parsesSyntheticFixtureWithYearRolloverAndFormattedAmounts() throws IOException {
        String text;
        try (InputStream fixture = Objects.requireNonNull(
                getClass().getResourceAsStream("/statement/synthetic-td-credit-card.txt"))) {
            text = new String(fixture.readAllBytes(), StandardCharsets.UTF_8);
        }

        List<ParsedTransactionDTO> parsed = parser.parseText(text);

        assertThat(parsed).extracting(ParsedTransactionDTO::date, ParsedTransactionDTO::type)
                .containsExactly(
                        tuple(LocalDate.of(2025, 12, 29), TransactionType.CREDIT),
                        tuple(LocalDate.of(2026, 1, 3), TransactionType.CREDIT),
                        tuple(LocalDate.of(2026, 1, 5), TransactionType.CREDIT));
        assertThat(parsed.get(0).amount()).isEqualByComparingTo("12.30");
        assertThat(parsed.get(1).amount()).isEqualByComparingTo("1234.56");
        assertThat(parsed).allSatisfy(transaction -> assertThat(transaction.bankName()).isEqualTo("TD Bank"));
    }

    @Test
    void parsesPdfAcrossPagesAndExcludesSidebarAndSummaryAmounts() throws IOException {
        byte[] pdf = SyntheticStatementPdf.statementWithSidebarAndContinuation();

        List<ParsedTransactionDTO> parsed = parser.parse(new ByteArrayInputStream(pdf));

        assertThat(parsed).extracting(ParsedTransactionDTO::description)
                .containsExactly("CORNER SHOP", "ONLINE SERVICE", "MERCHANT REFUND");
        assertThat(parsed.getFirst().date()).isEqualTo(LocalDate.of(2025, 12, 29));
        assertThat(parsed.get(0).amount()).isEqualByComparingTo("12.30");
        assertThat(parsed.get(1).amount()).isEqualByComparingTo("24.80");
        assertThat(parsed.get(2).amount()).isEqualByComparingTo("12.30");
        assertThat(parsed.get(2).type()).isEqualTo(TransactionType.CREDIT);
    }

    @ParameterizedTest
    @ValueSource(strings = {"-$12.34", "$12.34-", "$12.34 CR", "($12.34)"})
    void keepsCreditAmountConventionsPositive(String amount) {
        var parsed = parser.parseText(statement("January 15, 2026", "JAN 3 JAN 4 REFUND " + amount));

        assertThat(parsed).hasSize(1);
        assertThat(parsed.getFirst().amount()).isEqualByComparingTo("12.34");
        assertThat(parsed.getFirst().type()).isEqualTo(TransactionType.CREDIT);
    }

    @Test
    void assignsEveryCreditCardTransactionTheCreditType() {
        var parsed = parser.parseText(statement("January 15, 2026", """
                JAN 3 JAN 4 PURCHASE $12.34
                JAN 5 JAN 6 REFUND $2.34 CR
                """));

        assertThat(parsed).allSatisfy(
                transaction -> assertThat(transaction.type()).isEqualTo(TransactionType.CREDIT));
    }

    @ParameterizedTest
    @ValueSource(strings = {"JAN 3 JAN 4", "JAN. 3 JAN. 4", "JAN3 JAN4"})
    void parsesSupportedDayMonthSpacingAndPunctuation(String dates) {
        var parsed = parser.parseText(statement("January 15, 2026", dates + " CORNER SHOP $12.30"));

        assertThat(parsed.getFirst().date()).isEqualTo(LocalDate.of(2026, 1, 3));
    }

    @Test
    void preservesUnicodeAndPunctuationInWrappedDescriptions() {
        var parsed = parser.parseText(statement("January 15, 2026", """
                JAN 3 JAN 4 CAFÉ—CITY #42 / A&B (ONLINE) $12.30
                ORDER 12345 O'BRIEN
                JAN 5 JAN 6 ANOTHER SHOP $5.00
                """));

        assertThat(parsed).hasSize(2);
        assertThat(parsed.getFirst().description())
                .isEqualTo("CAFÉ—CITY #42 / A&B (ONLINE) ORDER 12345 O'BRIEN");
        assertThat(parsed.get(1).description()).isEqualTo("ANOTHER SHOP");
    }

    @Test
    void acceptsFebruaryTwentyNinthInALeapYear() {
        var parsed = parser.parseText(statement("March 15, 2024", "FEB 29 MAR 1 LEAP DAY SHOP $1.00"));

        assertThat(parsed.getFirst().date()).isEqualTo(LocalDate.of(2024, 2, 29));
    }

    @Test
    void rejectsImpossibleCalendarDates() {
        assertThatThrownBy(() -> parser.parseText(
                statement("March 15, 2025", "FEB 29 MAR 1 INVALID DATE SHOP $1.00")))
                .isInstanceOf(StatementParsingException.class);
    }

    @Test
    void requiresStatementDateToResolveTransactionYears() {
        String text = "TD CASH BACK VISA\n" + TABLE_HEADER + "JAN 3 JAN 4 CORNER SHOP $12.30\n";

        assertThatThrownBy(() -> parser.parseText(text))
                .isInstanceOf(StatementParsingException.class);
    }

    @Test
    void rejectsRecognizedTransactionRowsWithMissingAmounts() {
        assertThatThrownBy(() -> parser.parseText(statement("January 15, 2026", """
                JAN 3 JAN 4 CORNER SHOP $12.30
                JAN 5 JAN 6 TRANSACTION WITHOUT AMOUNT
                """)))
                .isInstanceOf(StatementParsingException.class);
    }

    @Test
    void rejectsRecognizedTransactionRowsWithMissingDescriptions() {
        assertThatThrownBy(() -> parser.parseText(statement("January 15, 2026", "JAN 3 JAN 4 $12.30")))
                .isInstanceOf(StatementParsingException.class);
    }

    @Test
    void rejectsUnreadablePdfStreams() {
        InputStream input = new ByteArrayInputStream("not a PDF".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> parser.parse(input))
                .isInstanceOf(StatementParsingException.class);
    }

    @Test
    void readsOwnerEncryptedBankPdfThatAllowsExtractionWithoutPassword() throws IOException {
        var transactions = parser.parse(new ByteArrayInputStream(SyntheticStatementPdf.ownerEncrypted(true)));

        assertThat(transactions).hasSize(3);
    }

    private String statement(String date, String rows) {
        return "TD CASH BACK VISA\nSTATEMENT DATE: " + date + "\n" + TABLE_HEADER + rows + "\n";
    }
}
