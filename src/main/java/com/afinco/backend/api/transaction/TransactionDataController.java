package com.afinco.backend.api.transaction;

import com.afinco.backend.api.transaction.dto.TransactionDataDeletionResponse;
import com.afinco.backend.api.transaction.dto.TransactionDataSummaryResponse;
import com.afinco.backend.service.TransactionDataService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The workspace's transaction data as a single resource, so a full reset is an explicit, separate
 * operation rather than a collection-wide DELETE on {@code /transactions} that a malformed id could reach.
 */
@RestController
@RequestMapping("/api/v1/transaction-data")
public class TransactionDataController {

    private final TransactionDataService transactionDataService;

    public TransactionDataController(TransactionDataService transactionDataService) {
        this.transactionDataService = transactionDataService;
    }

    @GetMapping
    public ResponseEntity<TransactionDataSummaryResponse> summarize() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(transactionDataService.summarize());
    }

    @DeleteMapping
    public TransactionDataDeletionResponse deleteAll() {
        return transactionDataService.deleteAll();
    }
}
