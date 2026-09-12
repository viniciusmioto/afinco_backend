package com.afinco.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.afinco.backend.api.transaction.dto.AccountResponse;
import com.afinco.backend.api.transaction.dto.CategoryResponse;
import com.afinco.backend.api.transaction.dto.TransactionBatchItemRequest;
import com.afinco.backend.api.transaction.dto.TransactionBatchRequest;
import com.afinco.backend.api.transaction.dto.TransactionBatchResponse;
import com.afinco.backend.api.transaction.dto.TransactionCreateRequest;
import com.afinco.backend.api.transaction.dto.TransactionResponse;
import com.afinco.backend.domain.Account;
import com.afinco.backend.domain.ExpenseType;
import com.afinco.backend.domain.Category;
import com.afinco.backend.domain.Transaction;
import com.afinco.backend.domain.TransactionStatus;
import com.afinco.backend.domain.TransactionType;
import com.afinco.backend.exception.ResourceNotFoundException;
import com.afinco.backend.mapper.TransactionMapper;
import com.afinco.backend.repository.AccountRepository;
import com.afinco.backend.repository.CategoryRepository;
import com.afinco.backend.repository.TransactionRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultTransactionServiceBatchTest {

    private static final String KNOWN_HASH = "a".repeat(64);
    private static final String FRESH_HASH = "b".repeat(64);

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TransactionMapper transactionMapper;

    @Mock
    private TransactionSignatureService signatureService;

    private DefaultTransactionService service;
    private Account account;
    private Category category;

    @BeforeEach
    void setUp() {
        service = new DefaultTransactionService(
                transactionRepository, accountRepository, categoryRepository, transactionMapper, signatureService);
        account = withId(new Account("TD Bank", "2048", "CAD"), 4L);
        category = withId(new Category("Groceries", ExpenseType.VARIABLE, "#2563EB"), 7L);
        when(accountRepository.findById(4L)).thenReturn(Optional.of(account));
        when(categoryRepository.findAllById(any())).thenReturn(List.of(category));
        when(transactionMapper.toEntity(any(), any(), any(), any(), any()))
                .thenReturn(transactionEntity());
        when(transactionMapper.toResponse(any())).thenReturn(response(TransactionStatus.CONFIRMED));
    }

    @Test
    void confirmsRowsWithoutAMatchingSignature() {
        when(signatureService.calculate(any(), any(), any(), any())).thenReturn(FRESH_HASH);
        when(transactionRepository.findExistingHashSignatures(anyList())).thenReturn(Set.of());
        when(transactionRepository.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));

        TransactionBatchResponse result = service.createBatch(request(item("Harbour Market", false)));

        assertThat(result.savedCount()).isEqualTo(1);
        assertThat(result.duplicateCount()).isZero();
        assertThat(capturedStatuses()).containsExactly(TransactionStatus.CONFIRMED);
    }

    @Test
    void marksAKnownSignatureAsPendingWhenTheReviewerDidNotForceIt() {
        when(signatureService.calculate(any(), any(), any(), any())).thenReturn(KNOWN_HASH);
        when(transactionRepository.findExistingHashSignatures(anyList())).thenReturn(Set.of(KNOWN_HASH));
        when(transactionRepository.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));

        TransactionBatchResponse result = service.createBatch(request(item("Harbour Market", false)));

        assertThat(result.duplicateCount()).isEqualTo(1);
        assertThat(capturedStatuses()).containsExactly(TransactionStatus.DUPLICATE_PENDING);
    }

    @Test
    void confirmsAKnownSignatureTheReviewerForceImported() {
        when(signatureService.calculate(any(), any(), any(), any())).thenReturn(KNOWN_HASH);
        when(transactionRepository.findExistingHashSignatures(anyList())).thenReturn(Set.of(KNOWN_HASH));
        when(transactionRepository.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));

        TransactionBatchResponse result = service.createBatch(request(item("Harbour Market", true)));

        assertThat(result.duplicateCount()).isZero();
        assertThat(capturedStatuses()).containsExactly(TransactionStatus.CONFIRMED);
    }

    @Test
    void flagsTheSecondIdenticalRowInsideTheSameBatch() {
        when(signatureService.calculate(any(), any(), any(), any())).thenReturn(KNOWN_HASH);
        when(transactionRepository.findExistingHashSignatures(anyList())).thenReturn(Set.of());
        when(transactionRepository.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));

        TransactionBatchResponse result = service.createBatch(
                request(item("Harbour Market", false), item("Harbour Market", false)));

        assertThat(result.savedCount()).isEqualTo(2);
        assertThat(result.duplicateCount()).isEqualTo(1);
        assertThat(capturedStatuses())
                .containsExactly(TransactionStatus.CONFIRMED, TransactionStatus.DUPLICATE_PENDING);
    }

    @Test
    void recalculatesTheSignatureFromTheResolvedAccountBankName() {
        when(signatureService.calculate(any(), any(), any(), any())).thenReturn(FRESH_HASH);
        when(transactionRepository.findExistingHashSignatures(anyList())).thenReturn(Set.of());
        when(transactionRepository.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));

        service.createBatch(request(item("Harbour Market", false)));

        verify(signatureService).calculate(
                LocalDate.of(2026, 9, 11), new BigDecimal("42.35"), "Harbour Market", "TD Bank");
    }

    @Test
    void ignoresThePreviewedClientSuppliedSignature() {
        when(signatureService.calculate(any(), any(), any(), any())).thenReturn(FRESH_HASH);
        when(transactionRepository.findExistingHashSignatures(anyList())).thenReturn(Set.of());
        when(transactionRepository.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));

        service.createBatch(request(item("Harbour Market", false)));

        ArgumentCaptor<TransactionCreateRequest> captor = ArgumentCaptor.forClass(TransactionCreateRequest.class);
        verify(transactionMapper).toEntity(captor.capture(), eq(account), eq(category), any(), eq(FRESH_HASH));
        assertThat(captor.getValue().hashSignature()).isNull();
        assertThat(captor.getValue().accountId()).isEqualTo(account.getId());
    }

    @Test
    void rejectsAnUnknownAccountBeforeSavingAnything() {
        when(accountRepository.findById(4L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createBatch(request(item("Harbour Market", false))))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(transactionRepository, never()).saveAll(anyList());
    }

    @Test
    void rejectsAnUnknownCategoryBeforeSavingAnything() {
        when(categoryRepository.findAllById(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.createBatch(request(item("Harbour Market", false))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category");

        verify(transactionRepository, never()).saveAll(anyList());
    }

    /** JPA assigns identifiers on persist, so unit tests set them directly. */
    private static <T> T withId(T entity, long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
            return entity;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not assign a test identifier", exception);
        }
    }

    private List<TransactionStatus> capturedStatuses() {
        ArgumentCaptor<TransactionStatus> captor = ArgumentCaptor.forClass(TransactionStatus.class);
        verify(transactionMapper, org.mockito.Mockito.atLeastOnce())
                .toEntity(any(), any(), any(), captor.capture(), any());
        return captor.getAllValues();
    }

    private static TransactionBatchRequest request(TransactionBatchItemRequest... items) {
        return new TransactionBatchRequest(4L, List.of(items));
    }

    private static TransactionBatchItemRequest item(String description, boolean forceDuplicate) {
        return new TransactionBatchItemRequest(
                7L,
                LocalDate.of(2026, 9, 11),
                new BigDecimal("42.35"),
                TransactionType.CREDIT,
                description,
                KNOWN_HASH,
                null,
                forceDuplicate);
    }

    private Transaction transactionEntity() {
        return new Transaction(
                account,
                category,
                LocalDate.of(2026, 9, 11),
                new BigDecimal("42.35"),
                TransactionType.CREDIT,
                "Harbour Market",
                FRESH_HASH,
                TransactionStatus.CONFIRMED,
                null);
    }

    private static TransactionResponse response(TransactionStatus status) {
        return new TransactionResponse(
                9L,
                new AccountResponse(4L, "TD Bank", "2048", "CAD"),
                new CategoryResponse(7L, "Groceries", ExpenseType.VARIABLE, "#2563EB"),
                LocalDate.of(2026, 9, 11),
                new BigDecimal("42.35"),
                TransactionType.CREDIT,
                "Harbour Market",
                FRESH_HASH,
                status,
                null,
                LocalDateTime.of(2026, 9, 11, 12, 0));
    }
}
