package com.afinco.backend.api.transaction;

import com.afinco.backend.api.transaction.dto.DuplicateResolutionRequest;
import com.afinco.backend.api.transaction.dto.PageResponse;
import com.afinco.backend.api.transaction.dto.TransactionBatchRequest;
import com.afinco.backend.api.transaction.dto.TransactionBatchResponse;
import com.afinco.backend.api.transaction.dto.TransactionCreateRequest;
import com.afinco.backend.api.transaction.dto.TransactionFilterRequest;
import com.afinco.backend.api.transaction.dto.TransactionResponse;
import com.afinco.backend.service.TransactionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping({"", "/"})
    public PageResponse<TransactionResponse> findTransactions(
            @Valid @ModelAttribute TransactionFilterRequest filters) {
        return transactionService.findTransactions(filters);
    }

    @PostMapping({"", "/"})
    public ResponseEntity<TransactionResponse> create(
            @Valid @RequestBody TransactionCreateRequest request) {
        TransactionResponse transaction = transactionService.create(request);
        URI location = URI.create("/api/v1/transactions/" + transaction.id());
        return ResponseEntity.created(location).body(transaction);
    }

    @PostMapping("/batch")
    public ResponseEntity<TransactionBatchResponse> createBatch(
            @Valid @RequestBody TransactionBatchRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transactionService.createBatch(request));
    }

    @PostMapping("/resolve-duplicate")
    public TransactionResponse resolveDuplicate(
            @Valid @RequestBody DuplicateResolutionRequest request) {
        return transactionService.resolveDuplicate(request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive long id) {
        transactionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
