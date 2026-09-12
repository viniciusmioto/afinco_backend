package com.afinco.backend.service;

import com.afinco.backend.api.statement.dto.ParsedTransactionResponse;
import com.afinco.backend.api.statement.dto.StatementUploadResponse;
import com.afinco.backend.domain.ExpenseType;
import com.afinco.backend.domain.StatementType;
import com.afinco.backend.domain.TransactionStatus;
import com.afinco.backend.exception.InvalidRequestException;
import com.afinco.backend.service.TransactionCategorizationService.Categorization;
import com.afinco.backend.statement.PdfTextExtractor;
import com.afinco.backend.statement.StatementDocument;
import com.afinco.backend.statement.StatementParser;
import com.afinco.backend.statement.StatementParserFactory;
import com.afinco.backend.statement.dto.ParsedStatement;
import com.afinco.backend.statement.dto.ParsedTransactionDTO;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Builds the review preview for an uploaded statement without saving anything.
 *
 * <p>Deliberately not transactional: PDF parsing is the slow part, and holding a transaction would pin
 * the single SQLite connection for its whole duration. Only the duplicate lookup touches the database,
 * so several uploads can be parsed concurrently.
 */
@Service
public class StatementUploadService {

    public static final int MAX_UPLOAD_BYTES = 10 * 1024 * 1024;

    private static final byte[] PDF_HEADER = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final PdfTextExtractor textExtractor;
    private final StatementParserFactory parserFactory;
    private final TransactionSignatureService signatureService;
    private final ExistingSignatureLookup existingSignatures;
    private final TransactionCategorizationService categorizationService;

    public StatementUploadService(
            PdfTextExtractor textExtractor,
            StatementParserFactory parserFactory,
            TransactionSignatureService signatureService,
            ExistingSignatureLookup existingSignatures,
            TransactionCategorizationService categorizationService) {
        this.textExtractor = textExtractor;
        this.parserFactory = parserFactory;
        this.signatureService = signatureService;
        this.existingSignatures = existingSignatures;
        this.categorizationService = categorizationService;
    }

    public StatementUploadResponse parse(byte[] pdfBytes, StatementType statementType) {
        validatePdf(pdfBytes);
        StatementDocument document = new StatementDocument(pdfBytes, textExtractor.extract(pdfBytes));
        StatementParser parser = parserFactory.getParser(statementType, document.text());
        ParsedStatement statement = parser.parse(document);

        List<SignedTransaction> signedTransactions = statement.transactions().stream()
                .map(transaction -> new SignedTransaction(transaction, signatureService.calculate(
                        transaction.date(), transaction.amount(), transaction.description(), transaction.bankName())))
                .toList();
        Set<String> seenHashes = existingSignatures.findExisting(
                signedTransactions.stream().map(SignedTransaction::hashSignature).toList());
        List<ParsedTransactionResponse> transactions = new ArrayList<>(signedTransactions.size());
        int duplicateCount = 0;
        for (SignedTransaction signed : signedTransactions) {
            boolean duplicate = !seenHashes.add(signed.hashSignature());
            if (duplicate) {
                duplicateCount++;
            }
            transactions.add(toResponse(signed, statementType, duplicate));
        }

        BigDecimal total = transactions.stream()
                .map(this::signedAmountForTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new StatementUploadResponse(
                statement.bankName(),
                statementType,
                statement.period().startDate(),
                statement.period().endDate(),
                transactions.size(),
                duplicateCount,
                total,
                transactions);
    }

    private void validatePdf(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            throw new InvalidRequestException("The uploaded PDF must not be empty");
        }
        if (bytes.length > MAX_UPLOAD_BYTES) {
            throw new MaxUploadSizeExceededException(MAX_UPLOAD_BYTES);
        }
        if (bytes.length < PDF_HEADER.length) {
            throw new InvalidRequestException("Uploaded file must be a PDF document");
        }
        for (int index = 0; index < PDF_HEADER.length; index++) {
            if (bytes[index] != PDF_HEADER[index]) {
                throw new InvalidRequestException("Uploaded file must be a PDF document");
            }
        }
    }

    private ParsedTransactionResponse toResponse(
            SignedTransaction signed, StatementType statementType, boolean duplicate) {
        ParsedTransactionDTO transaction = signed.transaction();
        Categorization categorization = categorizationService.categorize(transaction.description());
        return new ParsedTransactionResponse(
                transaction.date(), transaction.amount(), statementType.transactionType(), transaction.description(),
                transaction.bankName(), signed.hashSignature(),
                duplicate ? TransactionStatus.DUPLICATE_PENDING : TransactionStatus.CONFIRMED,
                duplicate,
                categorization.expenseType(),
                categorization.categoryName());
    }

    private BigDecimal signedAmountForTotal(ParsedTransactionResponse transaction) {
        return transaction.expenseType() == ExpenseType.PAYMENT
                ? transaction.amount().negate()
                : transaction.amount();
    }

    private record SignedTransaction(ParsedTransactionDTO transaction, String hashSignature) {
    }
}
