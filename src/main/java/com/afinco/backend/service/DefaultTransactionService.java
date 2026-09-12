package com.afinco.backend.service;

import com.afinco.backend.api.transaction.dto.DuplicateResolutionRequest;
import com.afinco.backend.api.transaction.dto.PageResponse;
import com.afinco.backend.api.transaction.dto.TransactionBatchItemRequest;
import com.afinco.backend.api.transaction.dto.TransactionBatchRequest;
import com.afinco.backend.api.transaction.dto.TransactionBatchResponse;
import com.afinco.backend.api.transaction.dto.TransactionCreateRequest;
import com.afinco.backend.api.transaction.dto.TransactionFilterRequest;
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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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

    private static final int HASH_QUERY_BATCH_SIZE = 400;

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
                filters.accountId(),
                filters.categoryId(),
                filters.type(),
                filters.status(),
                pageRequest);
        return transactionMapper.toPageResponse(transactions);
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
    public TransactionBatchResponse createBatch(TransactionBatchRequest request) {
        Account account = accountRepository.findById(request.accountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account", request.accountId()));
        Map<Long, Category> categories = resolveCategories(request.transactions());

        List<String> signatures = request.transactions().stream()
                .map(item -> signatureService.calculate(
                        item.date(), item.amount(), item.description(), account.getBankName()))
                .toList();
        Set<String> knownSignatures = existingSignatures(signatures);

        List<Transaction> transactions = new ArrayList<>(request.transactions().size());
        int duplicateCount = 0;
        for (int index = 0; index < request.transactions().size(); index++) {
            TransactionBatchItemRequest item = request.transactions().get(index);
            String signature = signatures.get(index);
            boolean matchesKnownSignature = !knownSignatures.add(signature);
            TransactionStatus status = matchesKnownSignature && !item.forceDuplicate()
                    ? TransactionStatus.DUPLICATE_PENDING
                    : TransactionStatus.CONFIRMED;
            if (status == TransactionStatus.DUPLICATE_PENDING) {
                duplicateCount++;
            }
            transactions.add(transactionMapper.toEntity(
                    item.toCreateRequest(account.getId()),
                    account,
                    categories.get(item.categoryId()),
                    status,
                    signature));
        }

        List<TransactionResponse> saved = transactionRepository.saveAll(transactions).stream()
                .map(transactionMapper::toResponse)
                .toList();
        return new TransactionBatchResponse(saved.size(), duplicateCount, saved);
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

    private Map<Long, Category> resolveCategories(List<TransactionBatchItemRequest> items) {
        Set<Long> requestedIds = items.stream()
                .map(TransactionBatchItemRequest::categoryId)
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

    private Set<String> existingSignatures(List<String> signatures) {
        List<String> distinct = signatures.stream().distinct().toList();
        Set<String> matches = new HashSet<>();
        for (int offset = 0; offset < distinct.size(); offset += HASH_QUERY_BATCH_SIZE) {
            int end = Math.min(offset + HASH_QUERY_BATCH_SIZE, distinct.size());
            matches.addAll(transactionRepository.findExistingHashSignatures(distinct.subList(offset, end)));
        }
        return matches;
    }
}
