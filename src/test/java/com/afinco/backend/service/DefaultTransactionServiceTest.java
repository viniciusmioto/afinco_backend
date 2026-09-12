package com.afinco.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.afinco.backend.api.transaction.dto.AccountResponse;
import com.afinco.backend.api.transaction.dto.CategoryResponse;
import com.afinco.backend.api.transaction.dto.DuplicateResolutionRequest;
import com.afinco.backend.api.transaction.dto.PageResponse;
import com.afinco.backend.api.transaction.dto.TransactionCreateRequest;
import com.afinco.backend.api.transaction.dto.TransactionFilterRequest;
import com.afinco.backend.api.transaction.dto.TransactionResponse;
import com.afinco.backend.domain.Account;
import com.afinco.backend.domain.ExpenseType;
import com.afinco.backend.domain.Category;
import com.afinco.backend.domain.Transaction;
import com.afinco.backend.domain.TransactionStatus;
import com.afinco.backend.domain.TransactionType;
import com.afinco.backend.exception.ConflictException;
import com.afinco.backend.exception.InvalidRequestException;
import com.afinco.backend.exception.ResourceNotFoundException;
import com.afinco.backend.mapper.TransactionMapper;
import com.afinco.backend.repository.AccountRepository;
import com.afinco.backend.repository.CategoryRepository;
import com.afinco.backend.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class DefaultTransactionServiceTest {

    private static final String HASH = "a".repeat(64);

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

    @BeforeEach
    void setUp() {
        service = new DefaultTransactionService(
                transactionRepository,
                accountRepository,
                categoryRepository,
                transactionMapper,
                signatureService);
    }

    @Test
    void returnsFilteredPageUsingSafePaginationDefaults() {
        TransactionFilterRequest filters = new TransactionFilterRequest(
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 9, 30),
                1L,
                2L,
                TransactionType.DEBIT,
                TransactionStatus.CONFIRMED,
                null,
                null);
        PageImpl<Transaction> repositoryPage = new PageImpl<>(List.of());
        PageResponse<TransactionResponse> expected = new PageResponse<>(List.of(), 0, 20, 0, 0, true, true);
        when(transactionRepository.findAllMatchingFilters(
                        eq(filters.startDate()),
                        eq(filters.endDate()),
                        eq(filters.accountId()),
                        eq(filters.categoryId()),
                        eq(filters.type()),
                        eq(filters.status()),
                        any(Pageable.class)))
                .thenReturn(repositoryPage);
        when(transactionMapper.toPageResponse(repositoryPage)).thenReturn(expected);

        PageResponse<TransactionResponse> result = service.findTransactions(filters);

        assertThat(result).isSameAs(expected);
        verify(transactionRepository).findAllMatchingFilters(
                eq(filters.startDate()),
                eq(filters.endDate()),
                eq(filters.accountId()),
                eq(filters.categoryId()),
                eq(filters.type()),
                eq(filters.status()),
                org.mockito.ArgumentMatchers.argThat(pageable ->
                        pageable.getPageNumber() == 0
                                && pageable.getPageSize() == 20
                                && pageable.getSort().getOrderFor("date").isDescending()));
    }

    @Test
    void createsConfirmedTransactionWhenHashIsNew() {
        Account account = account();
        Category category = category();
        TransactionCreateRequest request = createRequest();
        Transaction entity = transaction(TransactionStatus.CONFIRMED);
        TransactionResponse response = response(TransactionStatus.CONFIRMED);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category));
        when(transactionRepository.existsByHashSignature(HASH)).thenReturn(false);
        when(signatureService.calculate(request.date(), request.amount(), request.description(), account.getBankName()))
                .thenReturn(HASH);
        when(transactionMapper.toEntity(request, account, category, TransactionStatus.CONFIRMED, HASH)).thenReturn(entity);
        when(transactionRepository.save(entity)).thenReturn(entity);
        when(transactionMapper.toResponse(entity)).thenReturn(response);

        assertThat(service.create(request)).isSameAs(response);
    }

    @Test
    void flagsRepeatedHashAsPendingDuplicate() {
        Account account = account();
        Category category = category();
        TransactionCreateRequest request = createRequest();
        Transaction entity = transaction(TransactionStatus.DUPLICATE_PENDING);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category));
        when(transactionRepository.existsByHashSignature(HASH)).thenReturn(true);
        when(signatureService.calculate(request.date(), request.amount(), request.description(), account.getBankName()))
                .thenReturn(HASH);
        when(transactionMapper.toEntity(request, account, category, TransactionStatus.DUPLICATE_PENDING, HASH))
                .thenReturn(entity);
        when(transactionRepository.save(entity)).thenReturn(entity);

        service.create(request);

        verify(transactionMapper).toEntity(request, account, category, TransactionStatus.DUPLICATE_PENDING, HASH);
    }

    @Test
    void rejectsCreationForUnknownAccount() {
        TransactionCreateRequest request = createRequest();
        when(accountRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Account not found: 1");
        verify(categoryRepository, never()).findById(any());
    }

    @Test
    void confirmsPendingDuplicate() {
        Transaction entity = transaction(TransactionStatus.DUPLICATE_PENDING);
        TransactionResponse response = response(TransactionStatus.CONFIRMED);
        DuplicateResolutionRequest request = new DuplicateResolutionRequest(9L, TransactionStatus.CONFIRMED);
        when(transactionRepository.findById(9L)).thenReturn(Optional.of(entity));
        when(transactionRepository.save(entity)).thenReturn(entity);
        when(transactionMapper.toResponse(entity)).thenReturn(response);

        TransactionResponse result = service.resolveDuplicate(request);

        assertThat(entity.getStatus()).isEqualTo(TransactionStatus.CONFIRMED);
        assertThat(result).isSameAs(response);
    }

    @Test
    void rejectsResolutionForTransactionThatIsNotPending() {
        Transaction entity = transaction(TransactionStatus.CONFIRMED);
        when(transactionRepository.findById(9L)).thenReturn(Optional.of(entity));

        assertThatThrownBy(() -> service.resolveDuplicate(
                        new DuplicateResolutionRequest(9L, TransactionStatus.CONFIRMED)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not awaiting");
    }

    @Test
    void rejectsUnsupportedDuplicateStatus() {
        assertThatThrownBy(() -> service.resolveDuplicate(
                        new DuplicateResolutionRequest(9L, TransactionStatus.DUPLICATE_PENDING)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("CONFIRMED");
        verify(transactionRepository, never()).findById(any());
    }

    @Test
    void deletesExistingTransaction() {
        Transaction entity = transaction(TransactionStatus.CONFIRMED);
        when(transactionRepository.findById(9L)).thenReturn(Optional.of(entity));

        service.delete(9L);

        verify(transactionRepository).delete(entity);
    }

    @Test
    void reportsMissingTransactionDuringDelete() {
        when(transactionRepository.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(9L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Transaction not found: 9");
    }

    private TransactionCreateRequest createRequest() {
        return new TransactionCreateRequest(
                1L,
                2L,
                LocalDate.of(2026, 9, 11),
                new BigDecimal("42.35"),
                TransactionType.DEBIT,
                "Market",
                HASH,
                "raw line");
    }

    private Transaction transaction(TransactionStatus status) {
        return new Transaction(
                account(),
                category(),
                LocalDate.of(2026, 9, 11),
                new BigDecimal("42.35"),
                TransactionType.DEBIT,
                "Market",
                HASH,
                status,
                "raw line");
    }

    private Account account() {
        return new Account("TD Bank", "1234", "CAD");
    }

    private Category category() {
        return new Category("Groceries", ExpenseType.VARIABLE, "#2563EB");
    }

    private TransactionResponse response(TransactionStatus status) {
        return new TransactionResponse(
                9L,
                new AccountResponse(1L, "TD Bank", "1234", "CAD"),
                new CategoryResponse(2L, "Groceries", ExpenseType.VARIABLE, "#2563EB"),
                LocalDate.of(2026, 9, 11),
                new BigDecimal("42.35"),
                TransactionType.DEBIT,
                "Market",
                HASH,
                status,
                "raw line",
                null);
    }
}
