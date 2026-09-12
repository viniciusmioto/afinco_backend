package com.afinco.backend.service;

import com.afinco.backend.api.transaction.dto.TransactionDataDeletionResponse;
import com.afinco.backend.api.transaction.dto.TransactionDataSummaryResponse;

/**
 * Workspace-wide transaction data: every transaction (imported or manual) and every imported statement.
 * Accounts, categories, and users are reference data and are never part of it.
 */
public interface TransactionDataService {

    TransactionDataSummaryResponse summarize();

    /** Permanently removes all transactions and statements in one database transaction. */
    TransactionDataDeletionResponse deleteAll();
}
