package com.afinco.backend.service;

import com.afinco.backend.api.transaction.dto.DuplicateResolutionRequest;
import com.afinco.backend.api.transaction.dto.PageResponse;
import com.afinco.backend.api.transaction.dto.TransactionCreateRequest;
import com.afinco.backend.api.transaction.dto.TransactionFilterRequest;
import com.afinco.backend.api.transaction.dto.TransactionMonthResponse;
import com.afinco.backend.api.transaction.dto.TransactionResponse;
import java.util.List;

public interface TransactionService {

    PageResponse<TransactionResponse> findTransactions(TransactionFilterRequest filters);

    /** Calendar months that contain at least one transaction, newest first. */
    List<TransactionMonthResponse> findMonths();

    TransactionResponse create(TransactionCreateRequest request);

    TransactionResponse resolveDuplicate(DuplicateResolutionRequest request);

    void delete(long transactionId);
}
