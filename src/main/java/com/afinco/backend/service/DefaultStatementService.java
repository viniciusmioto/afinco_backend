package com.afinco.backend.service;

import com.afinco.backend.api.statement.dto.ImportedTransactionRequest;
import com.afinco.backend.api.statement.dto.StatementImportRequest;
import com.afinco.backend.api.statement.dto.StatementImportResponse;
import com.afinco.backend.api.statement.dto.StatementResponse;
import com.afinco.backend.domain.Account;
import com.afinco.backend.domain.Category;
import com.afinco.backend.domain.Statement;
import com.afinco.backend.domain.StatementPeriod;
import com.afinco.backend.domain.Transaction;
import com.afinco.backend.domain.TransactionStatus;
import com.afinco.backend.exception.ResourceNotFoundException;
import com.afinco.backend.mapper.StatementMapper;
import com.afinco.backend.mapper.TransactionMapper;
import com.afinco.backend.repository.AccountRepository;
import com.afinco.backend.repository.CategoryRepository;
import com.afinco.backend.repository.StatementRepository;
import com.afinco.backend.repository.TransactionRepository;
import com.afinco.backend.repository.projection.StatementTransactionCount;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DefaultStatementService implements StatementService {

    private final StatementRepository statementRepository;
    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final StatementMapper statementMapper;
    private final TransactionMapper transactionMapper;
    private final TransactionSignatureService signatureService;
    private final ExistingSignatureLookup existingSignatures;

    public DefaultStatementService(
            StatementRepository statementRepository,
            TransactionRepository transactionRepository,
            AccountRepository accountRepository,
            CategoryRepository categoryRepository,
            StatementMapper statementMapper,
            TransactionMapper transactionMapper,
            TransactionSignatureService signatureService,
            ExistingSignatureLookup existingSignatures) {
        this.statementRepository = statementRepository;
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.statementMapper = statementMapper;
        this.transactionMapper = transactionMapper;
        this.signatureService = signatureService;
        this.existingSignatures = existingSignatures;
    }

    @Override
    public List<StatementResponse> findStatements() {
        Map<Long, Long> counts = transactionRepository.countByStatement().stream()
                .collect(Collectors.toMap(
                        StatementTransactionCount::getStatementId, StatementTransactionCount::getTransactionCount));
        return statementRepository.findAllNewestFirst().stream()
                .map(statement -> statementMapper.toResponse(statement, counts.getOrDefault(statement.getId(), 0L)))
                .toList();
    }

    @Override
    @Transactional
    public StatementImportResponse importStatement(StatementImportRequest request) {
        Account account = accountRepository.findById(request.accountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account", request.accountId()));
        Map<Long, Category> categories = resolveCategories(request.transactions());
        StatementPeriod period = new StatementPeriod(request.periodStart(), request.periodEnd());

        Optional<Statement> existing = statementRepository.findByNaturalKey(
                account.getId(), request.statementType(), period.startDate(), period.endDate());
        Statement statement = existing.orElseGet(() ->
                statementRepository.save(new Statement(account, request.statementType(), period)));

        List<String> signatures = request.transactions().stream()
                .map(item -> signatureService.calculate(
                        item.date(), item.amount(), item.description(), account.getBankName()))
                .toList();
        Set<String> knownSignatures = existingSignatures.findExisting(signatures);

        List<Transaction> transactions = new ArrayList<>(signatures.size());
        int duplicateCount = 0;
        for (int index = 0; index < signatures.size(); index++) {
            ImportedTransactionRequest item = request.transactions().get(index);
            String signature = signatures.get(index);
            boolean matchesKnownSignature = !knownSignatures.add(signature);
            TransactionStatus status = matchesKnownSignature && !item.forceDuplicate()
                    ? TransactionStatus.DUPLICATE_PENDING
                    : TransactionStatus.CONFIRMED;
            if (status == TransactionStatus.DUPLICATE_PENDING) {
                duplicateCount++;
            }
            transactions.add(transactionMapper.toEntity(
                    item, statement, categories.get(item.categoryId()), status, signature));
        }
        transactionRepository.saveAll(transactions);

        long transactionCount = transactionRepository.countByStatementId(statement.getId());
        return new StatementImportResponse(
                statementMapper.toResponse(statement, transactionCount),
                existing.isEmpty(),
                transactions.size(),
                duplicateCount);
    }

    private Map<Long, Category> resolveCategories(List<ImportedTransactionRequest> items) {
        Set<Long> requestedIds = items.stream()
                .map(ImportedTransactionRequest::categoryId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Category> categories = categoryRepository.findAllById(requestedIds).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
        for (Long categoryId : requestedIds) {
            if (!categories.containsKey(categoryId)) {
                throw new ResourceNotFoundException("Category", categoryId);
            }
        }
        return categories;
    }
}
