package com.afinco.backend.statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.afinco.backend.api.statement.dto.ImportedTransactionRequest;
import com.afinco.backend.api.statement.dto.StatementImportRequest;
import com.afinco.backend.api.transaction.dto.DuplicateResolutionRequest;
import com.afinco.backend.api.transaction.dto.TransactionFilterRequest;
import com.afinco.backend.api.transaction.dto.TransactionCreateRequest;
import com.afinco.backend.domain.Account;
import com.afinco.backend.domain.StatementType;
import com.afinco.backend.domain.TransactionStatus;
import com.afinco.backend.exception.ConflictException;
import com.afinco.backend.exception.ResourceNotFoundException;
import com.afinco.backend.repository.AccountRepository;
import com.afinco.backend.repository.CategoryRepository;
import com.afinco.backend.repository.TransactionRepository;
import com.afinco.backend.service.StatementService;
import com.afinco.backend.service.StatementUploadService;
import com.afinco.backend.service.TransactionService;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StatementWorkflowIntegrationTest {
    @TempDir
    static Path databaseDirectory;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> "jdbc:sqlite:" + databaseDirectory.resolve("workflow.db"));
    }

    @Autowired
    private StatementUploadService uploads;
    @Autowired
    private TransactionService transactions;
    @Autowired
    private StatementService statements;
    @Autowired
    private AccountRepository accounts;
    @Autowired
    private CategoryRepository categories;
    @Autowired
    private TransactionRepository repository;

    @Test
    void previewsSavesDetectsAndResolvesDuplicatesUsingRealSqlite() throws Exception {
        byte[] pdf = SyntheticStatementPdf.ownerEncrypted(true);
        var preview = uploads.parse(pdf, StatementType.CREDIT_CARD);
        assertThat(preview.transactionCount()).isEqualTo(3);
        assertThat(preview.total()).isEqualByComparingTo("49.40");
        assertThat(preview.transactions()).allSatisfy(
                transaction -> assertThat(transaction.type()).isEqualTo(com.afinco.backend.domain.TransactionType.CREDIT));
        assertThat(repository.count()).isZero();
        var account = accounts.save(new Account("TD Canada Trust", "1234", "CAD"));
        var category = categories.findByName("Occasional").orElseThrow();
        var row = preview.transactions().getFirst();
        var request = new TransactionCreateRequest(account.getId(), category.getId(), row.date(),
                row.amount(), row.type(), row.description(), "f".repeat(64), null);

        var first = transactions.create(request);
        assertThat(first.hashSignature()).isEqualTo(row.hashSignature());
        assertThat(first.status()).isEqualTo(TransactionStatus.CONFIRMED);
        var repeatedPreview = uploads.parse(pdf, StatementType.CREDIT_CARD);
        assertThat(repeatedPreview.duplicateCount()).isEqualTo(1);
        assertThat(repeatedPreview.transactions().getFirst().status()).isEqualTo(TransactionStatus.DUPLICATE_PENDING);
        assertThat(repository.count()).isEqualTo(1);

        var duplicate = transactions.create(request);
        assertThat(duplicate.status()).isEqualTo(TransactionStatus.DUPLICATE_PENDING);
        var resolution = new DuplicateResolutionRequest(duplicate.id(), TransactionStatus.CONFIRMED);
        assertThat(transactions.resolveDuplicate(resolution).status()).isEqualTo(TransactionStatus.CONFIRMED);
        assertThatThrownBy(() -> transactions.resolveDuplicate(resolution)).isInstanceOf(ConflictException.class);

        transactions.delete(duplicate.id());
        assertThat(repository.findById(duplicate.id())).isEmpty();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void importsAReviewedPreviewAsAStatementAndAppendsARepeatedImport() throws Exception {
        var preview = uploads.parse(SyntheticStatementPdf.statementWithSidebarAndContinuation(), StatementType.CREDIT_CARD);
        assertThat(preview.periodStart()).isEqualTo(LocalDate.of(2025, 12, 16));
        assertThat(preview.periodEnd()).isEqualTo(LocalDate.of(2026, 1, 15));
        var account = accounts.save(new Account("TD Bank", "9876", "CAD"));
        var category = categories.findByName("Occasional").orElseThrow();
        var rows = preview.transactions().stream()
                .map(row -> new ImportedTransactionRequest(
                        category.getId(), row.date(), row.amount(), row.description(), false))
                .toList();

        var first = statements.importStatement(new StatementImportRequest(account.getId(),
                preview.statementType(), preview.periodStart(), preview.periodEnd(), rows.subList(0, 2)));
        var second = statements.importStatement(new StatementImportRequest(account.getId(),
                preview.statementType(), preview.periodStart(), preview.periodEnd(), rows.subList(2, 3)));

        assertThat(first.created()).isTrue();
        assertThat(second.created()).isFalse();
        assertThat(second.statement().id()).isEqualTo(first.statement().id());
        assertThat(second.statement().transactionCount()).isEqualTo(3);
        assertThat(statements.findStatements()).singleElement()
                .satisfies(statement -> assertThat(statement.transactionCount()).isEqualTo(3));

        var scoped = transactions.findTransactions(new TransactionFilterRequest(
                null, null, first.statement().id(), null, null, null, null, null, 500));
        assertThat(scoped.totalElements()).isEqualTo(3);
        assertThat(scoped.content()).allSatisfy(row ->
                assertThat(row.statement().id()).isEqualTo(first.statement().id()));
        assertThat(transactions.findMonths()).extracting(month -> month.month())
                .containsExactly(YearMonth.of(2026, 1), YearMonth.of(2025, 12));
    }

    @Test
    void reportsUnknownTransactionDuringDuplicateResolution() {
        assertThatThrownBy(() -> transactions.resolveDuplicate(
                new DuplicateResolutionRequest(999L, TransactionStatus.CONFIRMED)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
