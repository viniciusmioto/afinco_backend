package com.afinco.backend.service;

import com.afinco.backend.api.transaction.dto.DuplicateResolutionRequest;
import com.afinco.backend.api.transaction.dto.PageResponse;
import com.afinco.backend.api.transaction.dto.TransactionBatchRequest;
import com.afinco.backend.api.transaction.dto.TransactionBatchResponse;
import com.afinco.backend.api.transaction.dto.TransactionCreateRequest;
import com.afinco.backend.api.transaction.dto.TransactionFilterRequest;
import com.afinco.backend.api.transaction.dto.TransactionResponse;

public interface TransactionService {

    PageResponse<TransactionResponse> findTransactions(TransactionFilterRequest filters);

    TransactionResponse create(TransactionCreateRequest request);

    TransactionBatchResponse createBatch(TransactionBatchRequest request);

    TransactionResponse resolveDuplicate(DuplicateResolutionRequest request);

    void delete(long transactionId);
}
