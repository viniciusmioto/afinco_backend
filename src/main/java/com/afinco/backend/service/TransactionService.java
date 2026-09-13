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

    /** Calendar months that contain at least one transaction of the bank (every bank when null), newest first. */
    List<TransactionMonthResponse> findMonths(String bankName);

    TransactionResponse create(TransactionCreateRequest request);

    TransactionResponse resolveDuplicate(DuplicateResolutionRequest request);

    void delete(long transactionId);
}
