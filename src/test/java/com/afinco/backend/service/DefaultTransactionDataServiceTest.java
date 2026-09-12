package com.afinco.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import com.afinco.backend.api.transaction.dto.TransactionDataDeletionResponse;
import com.afinco.backend.repository.StatementRepository;
import com.afinco.backend.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultTransactionDataServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private StatementRepository statementRepository;

    @InjectMocks
    private DefaultTransactionDataService service;

    @Test
    void summarizesTransactionAndStatementCounts() {
        when(transactionRepository.count()).thenReturn(250L);
        when(statementRepository.count()).thenReturn(7L);

        assertThat(service.summarize().transactionCount()).isEqualTo(250L);
        assertThat(service.summarize().statementCount()).isEqualTo(7L);
    }

    @Test
    void deletesTransactionsBeforeTheStatementsTheyReference() {
        when(transactionRepository.count()).thenReturn(250L);
        when(statementRepository.count()).thenReturn(7L);

        TransactionDataDeletionResponse result = service.deleteAll();

        assertThat(result).isEqualTo(new TransactionDataDeletionResponse(250, 7));
        InOrder order = inOrder(transactionRepository, statementRepository);
        order.verify(transactionRepository).deleteAllInBatch();
        order.verify(statementRepository).deleteAllInBatch();
    }
}
