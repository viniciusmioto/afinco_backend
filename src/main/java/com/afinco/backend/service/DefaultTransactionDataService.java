package com.afinco.backend.service;

import com.afinco.backend.api.transaction.dto.TransactionDataDeletionResponse;
import com.afinco.backend.api.transaction.dto.TransactionDataSummaryResponse;
import com.afinco.backend.repository.StatementRepository;
import com.afinco.backend.repository.TransactionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
public class DefaultTransactionDataService implements TransactionDataService {

    private final TransactionRepository transactionRepository;
    private final StatementRepository statementRepository;

    public DefaultTransactionDataService(
            TransactionRepository transactionRepository,
            StatementRepository statementRepository) {
        this.transactionRepository = transactionRepository;
        this.statementRepository = statementRepository;
    }

    @Override
    public TransactionDataSummaryResponse summarize() {
        return new TransactionDataSummaryResponse(transactionRepository.count(), statementRepository.count());
    }

    @Override
    @Transactional
    public TransactionDataDeletionResponse deleteAll() {
        long transactions = transactionRepository.count();
        long statements = statementRepository.count();
        // Transactions reference statements with ON DELETE RESTRICT, so they must go first.
        transactionRepository.deleteAllInBatch();
        statementRepository.deleteAllInBatch();
        log.info("Deleted all transaction data: {} transactions and {} statements", transactions, statements);
        return new TransactionDataDeletionResponse(transactions, statements);
    }
}
