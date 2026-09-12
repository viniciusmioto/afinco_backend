package com.afinco.backend.service;

import com.afinco.backend.api.statement.dto.ParsedTransactionResponse;
import com.afinco.backend.api.statement.dto.StatementUploadResponse;
import com.afinco.backend.domain.ExpenseType;
import com.afinco.backend.domain.TransactionStatus;
import com.afinco.backend.domain.TransactionType;
import com.afinco.backend.exception.InvalidRequestException;
import com.afinco.backend.repository.TransactionRepository;
import com.afinco.backend.service.TransactionCategorizationService.Categorization;
import com.afinco.backend.statement.PdfTextExtractor;
import com.afinco.backend.statement.StatementParser;
import com.afinco.backend.statement.StatementParserFactory;
import com.afinco.backend.statement.StatementType;
import com.afinco.backend.statement.dto.ParsedTransactionDTO;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@Service
@Transactional(readOnly = true)
public class StatementUploadService {

    public static final int MAX_UPLOAD_BYTES = 10 * 1024 * 1024;

    private static final int HASH_QUERY_BATCH_SIZE = 400;
    private static final byte[] PDF_HEADER = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final PdfTextExtractor textExtractor;
    private final StatementParserFactory parserFactory;
    private final TransactionSignatureService signatureService;
    private final TransactionRepository transactionRepository;
    private final TransactionCategorizationService categorizationService;

    public StatementUploadService(
            PdfTextExtractor textExtractor,
            StatementParserFactory parserFactory,
            TransactionSignatureService signatureService,
            TransactionRepository transactionRepository,
            TransactionCategorizationService categorizationService) {
        this.textExtractor = textExtractor;
        this.parserFactory = parserFactory;
        this.signatureService = signatureService;
        this.transactionRepository = transactionRepository;
        this.categorizationService = categorizationService;
    }

    public StatementUploadResponse parse(byte[] pdfBytes, StatementType statementType) {
        validatePdf(pdfBytes);
        String documentText = textExtractor.extract(pdfBytes);
        StatementParser parser = parserFactory.getParser(statementType, documentText);
        List<SignedTransaction> signedTransactions = parser.parse(new ByteArrayInputStream(pdfBytes)).stream()
                .map(transaction -> assignType(transaction, statementType))
                .map(transaction -> new SignedTransaction(transaction, signatureService.calculate(
                        transaction.date(), transaction.amount(), transaction.description(), transaction.bankName())))
                .toList();
        Set<String> seenHashes = existingHashes(signedTransactions);
        List<ParsedTransactionResponse> transactions = new ArrayList<>(signedTransactions.size());
        int duplicateCount = 0;
        for (SignedTransaction signed : signedTransactions) {
            boolean duplicate = !seenHashes.add(signed.hashSignature());
            if (duplicate) {
                duplicateCount++;
            }
            transactions.add(toResponse(signed, duplicate));
        }

        BigDecimal total = transactions.stream()
                .map(this::signedAmountForTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new StatementUploadResponse(
                parser.bankName(), transactions.size(), duplicateCount, total, transactions);
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

    private Set<String> existingHashes(List<SignedTransaction> transactions) {
        List<String> hashes = transactions.stream().map(SignedTransaction::hashSignature).distinct().toList();
        Set<String> matches = new HashSet<>();
        for (int offset = 0; offset < hashes.size(); offset += HASH_QUERY_BATCH_SIZE) {
            int end = Math.min(offset + HASH_QUERY_BATCH_SIZE, hashes.size());
            matches.addAll(transactionRepository.findExistingHashSignatures(hashes.subList(offset, end)));
        }
        return matches;
    }

    private ParsedTransactionResponse toResponse(SignedTransaction signed, boolean duplicate) {
        ParsedTransactionDTO transaction = signed.transaction();
        Categorization categorization = categorizationService.categorize(transaction.description());
        return new ParsedTransactionResponse(
                transaction.date(), transaction.amount(), transaction.type(), transaction.description(),
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

    private ParsedTransactionDTO assignType(ParsedTransactionDTO transaction, StatementType statementType) {
        TransactionType type = statementType == StatementType.CREDIT_CARD
                ? TransactionType.CREDIT
                : TransactionType.DEBIT;
        return new ParsedTransactionDTO(transaction.date(), transaction.amount(), type,
                transaction.description(), transaction.bankName());
    }

    private record SignedTransaction(ParsedTransactionDTO transaction, String hashSignature) {
    }
}
