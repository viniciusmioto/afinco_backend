package com.afinco.backend.service;

import com.afinco.backend.api.transaction.dto.DuplicateResolutionRequest;
import com.afinco.backend.api.transaction.dto.PageResponse;
import com.afinco.backend.api.transaction.dto.TransactionCreateRequest;
import com.afinco.backend.api.transaction.dto.TransactionFilterRequest;
import com.afinco.backend.api.transaction.dto.TransactionMonthResponse;
import com.afinco.backend.api.transaction.dto.TransactionResponse;
import com.afinco.backend.domain.Account;
import com.afinco.backend.domain.Category;
import com.afinco.backend.domain.Transaction;
import com.afinco.backend.domain.TransactionStatus;
import com.afinco.backend.exception.ConflictException;
import com.afinco.backend.exception.InvalidRequestException;
import com.afinco.backend.exception.ResourceNotFoundException;
import com.afinco.backend.mapper.TransactionMapper;
import com.afinco.backend.repository.AccountRepository;
import com.afinco.backend.repository.CategoryRepository;
import com.afinco.backend.repository.TransactionRepository;
import com.afinco.backend.repository.projection.DailyTransactionCount;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DefaultTransactionService implements TransactionService {

    private static final Sort DEFAULT_SORT = Sort.by(
            Sort.Order.desc("date"),
            Sort.Order.desc("id"));

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionMapper transactionMapper;
    private final TransactionSignatureService signatureService;

    public DefaultTransactionService(
            TransactionRepository transactionRepository,
            AccountRepository accountRepository,
            CategoryRepository categoryRepository,
            TransactionMapper transactionMapper,
            TransactionSignatureService signatureService) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.transactionMapper = transactionMapper;
        this.signatureService = signatureService;
    }

    @Override
    public PageResponse<TransactionResponse> findTransactions(TransactionFilterRequest filters) {
        PageRequest pageRequest = PageRequest.of(filters.resolvedPage(), filters.resolvedSize(), DEFAULT_SORT);
        Page<Transaction> transactions = transactionRepository.findAllMatchingFilters(
                filters.startDate(),
                filters.endDate(),
                filters.statementId(),
                filters.accountId(),
                filters.categoryId(),
                filters.type(),
                filters.status(),
                pageRequest);
        return transactionMapper.toPageResponse(transactions);
    }

    @Override
    public List<TransactionMonthResponse> findMonths() {
        Map<YearMonth, Long> counts = new TreeMap<>(Comparator.reverseOrder());
        for (DailyTransactionCount day : transactionRepository.countByDate()) {
            counts.merge(YearMonth.from(day.getDate()), day.getTransactionCount(), Long::sum);
        }
        return counts.entrySet().stream()
                .map(entry -> new TransactionMonthResponse(entry.getKey(), entry.getValue()))
                .toList();
    }

    @Override
    @Transactional
    public TransactionResponse create(TransactionCreateRequest request) {
        Account account = accountRepository.findById(request.accountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account", request.accountId()));
        Category category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.categoryId()));
        String hashSignature = signatureService.calculate(
                request.date(), request.amount(), request.description(), account.getBankName());
        TransactionStatus status = transactionRepository.existsByHashSignature(hashSignature)
                ? TransactionStatus.DUPLICATE_PENDING
                : TransactionStatus.CONFIRMED;
        Transaction transaction = transactionMapper.toEntity(request, account, category, status, hashSignature);

        return transactionMapper.toResponse(transactionRepository.save(transaction));
    }

    @Override
    @Transactional
    public TransactionResponse resolveDuplicate(DuplicateResolutionRequest request) {
        if (request.status() != TransactionStatus.CONFIRMED) {
            throw new InvalidRequestException("A duplicate can only be resolved to CONFIRMED");
        }

        Transaction transaction = transactionRepository.findById(request.transactionId())
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", request.transactionId()));
        if (transaction.getStatus() != TransactionStatus.DUPLICATE_PENDING) {
            throw new ConflictException("Transaction is not awaiting duplicate resolution");
        }

        transaction.confirmDuplicate();
        return transactionMapper.toResponse(transactionRepository.save(transaction));
    }

    @Override
    @Transactional
    public void delete(long transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction", transactionId));
        transactionRepository.delete(transaction);
    }
}
