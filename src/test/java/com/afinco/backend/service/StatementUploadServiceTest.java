package com.afinco.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.afinco.backend.api.statement.dto.StatementUploadResponse;
import com.afinco.backend.domain.TransactionStatus;
import com.afinco.backend.domain.TransactionType;
import com.afinco.backend.exception.InvalidRequestException;
import com.afinco.backend.exception.StatementParsingException;
import com.afinco.backend.exception.UnsupportedStatementException;
import com.afinco.backend.repository.TransactionRepository;
import com.afinco.backend.statement.PdfTextExtractor;
import com.afinco.backend.statement.StatementParser;
import com.afinco.backend.statement.StatementParserFactory;
import com.afinco.backend.statement.StatementType;
import com.afinco.backend.statement.dto.ParsedTransactionDTO;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatementUploadServiceTest {

    private static final byte[] PDF_BYTES = "%PDF-1.7 synthetic fixture".getBytes(StandardCharsets.US_ASCII);
    private static final String BANK_NAME = "TD Bank";
    private static final String DOCUMENT_TEXT = "TD Bank synthetic statement";

    @Mock
    private PdfTextExtractor textExtractor;

    @Mock
    private StatementParserFactory parserFactory;

    @Mock
    private StatementParser parser;

    @Mock
    private TransactionRepository transactionRepository;

    private StatementUploadService service;

    private final TransactionSignatureService signatureService = new TransactionSignatureService();
    private final TransactionCategorizationService categorizationService = new TransactionCategorizationService();

    @BeforeEach
    void setUp() {
        service = new StatementUploadService(
                textExtractor, parserFactory, signatureService, transactionRepository, categorizationService);
    }

    @Test
    void returnsParsedTransactionsWithDatabaseDuplicateFlagsWithoutSaving() {
        ParsedTransactionDTO purchase = transaction("Synthetic Market", "18.50");
        ParsedTransactionDTO payment = transaction("Synthetic Payment", "70.00");
        prepareParser(List.of(purchase, payment));
        String existingHash = signature(payment);
        when(transactionRepository.findExistingHashSignatures(any())).thenReturn(Set.of(existingHash));

        StatementUploadResponse result = service.parse(PDF_BYTES, StatementType.CREDIT_CARD);

        assertThat(result.bankName()).isEqualTo(BANK_NAME);
        assertThat(result.transactionCount()).isEqualTo(2);
        assertThat(result.duplicateCount()).isEqualTo(1);
        assertThat(result.total()).isEqualByComparingTo("88.50");
        assertThat(result.transactions()).allSatisfy(
                transaction -> assertThat(transaction.type()).isEqualTo(TransactionType.CREDIT));
        assertThat(result.transactions().getFirst().description()).isEqualTo("Synthetic Market");
        assertThat(result.transactions().getFirst().status()).isEqualTo(TransactionStatus.CONFIRMED);
        assertThat(result.transactions().getFirst().duplicate()).isFalse();
        assertThat(result.transactions().getLast().hashSignature()).isEqualTo(existingHash);
        assertThat(result.transactions().getLast().status()).isEqualTo(TransactionStatus.DUPLICATE_PENDING);
        assertThat(result.transactions().getLast().duplicate()).isTrue();
        verify(transactionRepository).findExistingHashSignatures(List.of(signature(purchase), existingHash));
        org.mockito.Mockito.verifyNoMoreInteractions(transactionRepository);
    }

    @Test
    void flagsRepeatedRowsWithinTheSameUpload() {
        ParsedTransactionDTO purchase = transaction("Synthetic Market", "18.50");
        prepareParser(List.of(purchase, purchase, purchase));
        when(transactionRepository.findExistingHashSignatures(any())).thenReturn(Set.of());

        StatementUploadResponse result = service.parse(PDF_BYTES, StatementType.CREDIT_CARD);

        assertThat(result.transactions()).extracting(item -> item.status())
                .containsExactly(TransactionStatus.CONFIRMED,
                        TransactionStatus.DUPLICATE_PENDING, TransactionStatus.DUPLICATE_PENDING);
        assertThat(result.duplicateCount()).isEqualTo(2);
        verify(transactionRepository).findExistingHashSignatures(List.of(signature(purchase)));
    }

    @Test
    void batchesHashLookupsBelowSQLiteParameterLimit() {
        List<ParsedTransactionDTO> transactions = IntStream.range(0, 801)
                .mapToObj(index -> transaction("Synthetic Merchant " + index, "1.00"))
                .toList();
        prepareParser(transactions);
        when(transactionRepository.findExistingHashSignatures(any())).thenReturn(Set.of());

        StatementUploadResponse result = service.parse(PDF_BYTES, StatementType.CREDIT_CARD);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(transactionRepository, org.mockito.Mockito.times(3)).findExistingHashSignatures(captor.capture());
        assertThat(captor.getAllValues()).extracting(Collection::size).containsExactly(400, 400, 1);
        assertThat(result.transactionCount()).isEqualTo(801);
    }

    @Test
    void returnsEmptyPreviewWithoutQueryingDatabase() {
        prepareParser(List.of());

        StatementUploadResponse result = service.parse(PDF_BYTES, StatementType.CREDIT_CARD);

        assertThat(result.transactions()).isEmpty();
        assertThat(result.transactionCount()).isZero();
        assertThat(result.duplicateCount()).isZero();
        assertThat(result.total()).isEqualByComparingTo(BigDecimal.ZERO);
        verifyNoInteractions(transactionRepository);
    }

    @Test
    void rejectsEmptyOrNonPdfInputBeforeParsing() {
        for (byte[] invalid : List.of(new byte[0], "not a PDF".getBytes(StandardCharsets.US_ASCII))) {
            assertThatThrownBy(() -> service.parse(invalid, StatementType.CREDIT_CARD))
                    .isInstanceOf(InvalidRequestException.class);
        }
        verifyNoInteractions(textExtractor, parserFactory, transactionRepository);
    }

    @Test
    void rejectsUnsupportedBankWithoutQueryingDatabase() {
        when(textExtractor.extract(PDF_BYTES)).thenReturn("Other bank synthetic statement");
        when(parserFactory.getParser(StatementType.CREDIT_CARD, "Other bank synthetic statement"))
                .thenThrow(new UnsupportedStatementException("Unsupported statement"));

        assertThatThrownBy(() -> service.parse(PDF_BYTES, StatementType.CREDIT_CARD))
                .isInstanceOf(UnsupportedStatementException.class);

        verifyNoInteractions(transactionRepository);
    }

    @Test
    void rejectsCorruptPdfWithoutQueryingDatabase() {
        when(textExtractor.extract(PDF_BYTES)).thenThrow(new StatementParsingException("Unable to read PDF"));

        assertThatThrownBy(() -> service.parse(PDF_BYTES, StatementType.CREDIT_CARD))
                .isInstanceOf(StatementParsingException.class);

        verifyNoInteractions(parserFactory, transactionRepository);
    }

    @Test
    void passesOriginalPdfBytesToSelectedParser() throws Exception {
        prepareParser(List.of());

        service.parse(PDF_BYTES, StatementType.CREDIT_CARD);

        ArgumentCaptor<InputStream> captor = ArgumentCaptor.forClass(InputStream.class);
        verify(parser).parse(captor.capture());
        assertThat(captor.getValue().readAllBytes()).containsExactly(PDF_BYTES);
    }

    private void prepareParser(List<ParsedTransactionDTO> transactions) {
        when(textExtractor.extract(PDF_BYTES)).thenReturn(DOCUMENT_TEXT);
        when(parserFactory.getParser(StatementType.CREDIT_CARD, DOCUMENT_TEXT)).thenReturn(parser);
        when(parser.bankName()).thenReturn(BANK_NAME);
        when(parser.parse(any(InputStream.class))).thenReturn(transactions);
    }

    private ParsedTransactionDTO transaction(String description, String amount) {
        return new ParsedTransactionDTO(LocalDate.of(2026, 1, 15), new BigDecimal(amount),
                TransactionType.DEBIT, description, BANK_NAME);
    }

    private String signature(ParsedTransactionDTO transaction) {
        return signatureService.calculate(transaction.date(), transaction.amount(),
                transaction.description(), transaction.bankName());
    }
}
