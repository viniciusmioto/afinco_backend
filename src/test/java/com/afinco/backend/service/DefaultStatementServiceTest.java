package com.afinco.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.afinco.backend.api.statement.dto.ImportedTransactionRequest;
import com.afinco.backend.api.statement.dto.StatementImportRequest;
import com.afinco.backend.api.statement.dto.StatementImportResponse;
import com.afinco.backend.api.statement.dto.StatementResponse;
import com.afinco.backend.domain.Account;
import com.afinco.backend.domain.Category;
import com.afinco.backend.domain.ExpenseType;
import com.afinco.backend.domain.Statement;
import com.afinco.backend.domain.StatementPeriod;
import com.afinco.backend.domain.StatementType;
import com.afinco.backend.domain.Transaction;
import com.afinco.backend.domain.TransactionStatus;
import com.afinco.backend.domain.TransactionType;
import com.afinco.backend.exception.ResourceNotFoundException;
import com.afinco.backend.mapper.AccountMapper;
import com.afinco.backend.mapper.CategoryMapper;
import com.afinco.backend.mapper.StatementMapper;
import com.afinco.backend.mapper.TransactionMapper;
import com.afinco.backend.repository.AccountRepository;
import com.afinco.backend.repository.CategoryRepository;
import com.afinco.backend.repository.StatementRepository;
import com.afinco.backend.repository.TransactionRepository;
import com.afinco.backend.repository.projection.StatementTransactionCount;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
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
class DefaultStatementServiceTest {

    private static final String KNOWN_HASH = "a".repeat(64);
    private static final String FRESH_HASH = "b".repeat(64);
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 2, 3);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 2, 13);

    @Mock
    private StatementRepository statementRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TransactionSignatureService signatureService;

    private DefaultStatementService service;
    private Account account;
    private Category category;

    @BeforeEach
    void setUp() {
        StatementMapper statementMapper = new StatementMapper(new AccountMapper());
        TransactionMapper transactionMapper = new TransactionMapper(
                new AccountMapper(), new CategoryMapper(), statementMapper);
        service = new DefaultStatementService(
                statementRepository, transactionRepository, accountRepository, categoryRepository,
                statementMapper, transactionMapper, signatureService,
                new ExistingSignatureLookup(transactionRepository));
        account = withId(new Account("TD Bank", "2048", "CAD"), 4L);
        category = withId(new Category("Groceries", ExpenseType.VARIABLE, "#2563EB"), 7L);
        when(accountRepository.findById(4L)).thenReturn(Optional.of(account));
        when(categoryRepository.findAllById(any())).thenReturn(List.of(category));
        when(statementRepository.findByNaturalKey(4L, StatementType.CREDIT_CARD, PERIOD_START, PERIOD_END))
                .thenReturn(Optional.empty());
        when(statementRepository.save(any(Statement.class)))
                .thenAnswer(call -> withId(call.getArgument(0), 3L));
        when(transactionRepository.saveAll(anyList())).thenAnswer(call -> call.getArgument(0));
        when(signatureService.calculate(any(), any(), any(), any())).thenReturn(FRESH_HASH);
        when(transactionRepository.findExistingHashSignatures(anyList())).thenReturn(Set.of());
    }

    @Test
    void createsTheStatementAndLinksEveryConfirmedRowToIt() {
        when(transactionRepository.countByStatementId(3L)).thenReturn(1L);

        StatementImportResponse result = service.importStatement(request(item("Harbour Market", false)));

        assertThat(result.created()).isTrue();
        assertThat(result.savedCount()).isEqualTo(1);
        assertThat(result.duplicateCount()).isZero();
        assertThat(result.statement().id()).isEqualTo(3L);
        assertThat(result.statement().periodStart()).isEqualTo(PERIOD_START);
        assertThat(result.statement().transactionCount()).isEqualTo(1);

        Transaction saved = savedTransactions().getFirst();
        assertThat(saved.getStatement().getId()).isEqualTo(3L);
        assertThat(saved.getAccount()).isSameAs(account);
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.CONFIRMED);
        assertThat(saved.getType()).isEqualTo(TransactionType.CREDIT);
        assertThat(saved.getHashSignature()).isEqualTo(FRESH_HASH);
    }

    @Test
    void appendsToAnExistingStatementForTheSameAccountTypeAndPeriod() {
        Statement existing = withId(new Statement(
                account, StatementType.CREDIT_CARD, new StatementPeriod(PERIOD_START, PERIOD_END)), 8L);
        when(statementRepository.findByNaturalKey(4L, StatementType.CREDIT_CARD, PERIOD_START, PERIOD_END))
                .thenReturn(Optional.of(existing));
        when(transactionRepository.countByStatementId(8L)).thenReturn(43L);

        StatementImportResponse result = service.importStatement(request(item("Harbour Market", false)));

        assertThat(result.created()).isFalse();
        assertThat(result.statement().id()).isEqualTo(8L);
        assertThat(result.statement().transactionCount()).isEqualTo(43L);
        assertThat(savedTransactions().getFirst().getStatement()).isSameAs(existing);
        verify(statementRepository, never()).save(any());
    }

    @Test
    void marksAKnownSignatureAsPendingWhenTheReviewerDidNotForceIt() {
        when(signatureService.calculate(any(), any(), any(), any())).thenReturn(KNOWN_HASH);
        when(transactionRepository.findExistingHashSignatures(anyList())).thenReturn(Set.of(KNOWN_HASH));

        StatementImportResponse result = service.importStatement(request(item("Harbour Market", false)));

        assertThat(result.duplicateCount()).isEqualTo(1);
        assertThat(savedTransactions()).extracting(Transaction::getStatus)
                .containsExactly(TransactionStatus.DUPLICATE_PENDING);
    }

    @Test
    void confirmsAKnownSignatureTheReviewerForceImported() {
        when(signatureService.calculate(any(), any(), any(), any())).thenReturn(KNOWN_HASH);
        when(transactionRepository.findExistingHashSignatures(anyList())).thenReturn(Set.of(KNOWN_HASH));

        StatementImportResponse result = service.importStatement(request(item("Harbour Market", true)));

        assertThat(result.duplicateCount()).isZero();
        assertThat(savedTransactions()).extracting(Transaction::getStatus)
                .containsExactly(TransactionStatus.CONFIRMED);
    }

    @Test
    void flagsTheSecondIdenticalRowInsideTheSameImport() {
        when(signatureService.calculate(any(), any(), any(), any())).thenReturn(KNOWN_HASH);

        StatementImportResponse result = service.importStatement(
                request(item("Harbour Market", false), item("Harbour Market", false)));

        assertThat(result.savedCount()).isEqualTo(2);
        assertThat(result.duplicateCount()).isEqualTo(1);
        assertThat(savedTransactions()).extracting(Transaction::getStatus)
                .containsExactly(TransactionStatus.CONFIRMED, TransactionStatus.DUPLICATE_PENDING);
    }

    @Test
    void recalculatesTheSignatureFromTheResolvedAccountBankName() {
        service.importStatement(request(item("Harbour Market", false)));

        verify(signatureService).calculate(
                LocalDate.of(2026, 2, 11), new BigDecimal("42.35"), "Harbour Market", "TD Bank");
    }

    @Test
    void rejectsAnUnknownAccountBeforeCreatingAStatement() {
        when(accountRepository.findById(4L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.importStatement(request(item("Harbour Market", false))))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(statementRepository, never()).save(any());
        verify(transactionRepository, never()).saveAll(anyList());
    }

    @Test
    void rejectsAnUnknownCategoryBeforeCreatingAStatement() {
        when(categoryRepository.findAllById(any())).thenReturn(List.of());

        assertThatThrownBy(() -> service.importStatement(request(item("Harbour Market", false))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category");

        verify(statementRepository, never()).save(any());
        verify(transactionRepository, never()).saveAll(anyList());
    }

    @Test
    void listsStatementsNewestFirstWithTheirTransactionCounts() {
        Statement february = withId(new Statement(
                account, StatementType.CREDIT_CARD, new StatementPeriod(PERIOD_START, PERIOD_END)), 3L);
        Statement empty = withId(new Statement(account, StatementType.CREDIT_CARD,
                new StatementPeriod(LocalDate.of(2026, 1, 3), LocalDate.of(2026, 2, 2))), 2L);
        when(statementRepository.findAllNewestFirst()).thenReturn(List.of(february, empty));
        when(transactionRepository.countByStatement()).thenReturn(List.of(count(3L, 42L)));

        List<StatementResponse> statements = service.findStatements();

        assertThat(statements).extracting(StatementResponse::id).containsExactly(3L, 2L);
        assertThat(statements).extracting(StatementResponse::transactionCount).containsExactly(42L, 0L);
        assertThat(statements.getFirst().account().bankName()).isEqualTo("TD Bank");
    }

    @SuppressWarnings("unchecked")
    private List<Transaction> savedTransactions() {
        ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
        verify(transactionRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private static StatementTransactionCount count(long statementId, long transactionCount) {
        return new StatementTransactionCount() {
            @Override
            public Long getStatementId() {
                return statementId;
            }

            @Override
            public long getTransactionCount() {
                return transactionCount;
            }
        };
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

    private static StatementImportRequest request(ImportedTransactionRequest... items) {
        return new StatementImportRequest(
                4L, StatementType.CREDIT_CARD, PERIOD_START, PERIOD_END, List.of(items));
    }

    private static ImportedTransactionRequest item(String description, boolean forceDuplicate) {
        return new ImportedTransactionRequest(
                7L, LocalDate.of(2026, 2, 11), new BigDecimal("42.35"), description, forceDuplicate);
    }
}
