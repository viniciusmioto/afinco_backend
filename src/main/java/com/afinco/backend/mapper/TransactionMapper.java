package com.afinco.backend.mapper;

import com.afinco.backend.api.transaction.dto.PageResponse;
import com.afinco.backend.api.transaction.dto.TransactionCreateRequest;
import com.afinco.backend.api.transaction.dto.TransactionResponse;
import com.afinco.backend.domain.Account;
import com.afinco.backend.domain.Category;
import com.afinco.backend.domain.Transaction;
import com.afinco.backend.domain.TransactionStatus;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class TransactionMapper {

    private final AccountMapper accountMapper;
    private final CategoryMapper categoryMapper;

    public TransactionMapper(AccountMapper accountMapper, CategoryMapper categoryMapper) {
        this.accountMapper = accountMapper;
        this.categoryMapper = categoryMapper;
    }

    public Transaction toEntity(
            TransactionCreateRequest request,
            Account account,
            Category category,
            TransactionStatus status,
            String hashSignature) {
        Objects.requireNonNull(request, "Transaction request must not be null");
        return new Transaction(
                account,
                category,
                request.date(),
                request.amount(),
                request.type(),
                request.description(),
                hashSignature,
                status,
                request.rawText());
    }

    public TransactionResponse toResponse(Transaction transaction) {
        Objects.requireNonNull(transaction, "Transaction must not be null");
        return new TransactionResponse(
                transaction.getId(),
                accountMapper.toResponse(transaction.getAccount()),
                categoryMapper.toResponse(transaction.getCategory()),
                transaction.getDate(),
                transaction.getAmount(),
                transaction.getType(),
                transaction.getDescription(),
                transaction.getHashSignature(),
                transaction.getStatus(),
                transaction.getRawText(),
                transaction.getCreatedAt());
    }

    public PageResponse<TransactionResponse> toPageResponse(Page<Transaction> transactions) {
        return new PageResponse<>(
                transactions.getContent().stream().map(this::toResponse).toList(),
                transactions.getNumber(),
                transactions.getSize(),
                transactions.getTotalElements(),
                transactions.getTotalPages(),
                transactions.isFirst(),
                transactions.isLast());
    }
}
