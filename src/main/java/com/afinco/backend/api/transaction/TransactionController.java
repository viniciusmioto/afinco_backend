package com.afinco.backend.api.transaction;

import com.afinco.backend.api.transaction.dto.DuplicateResolutionRequest;
import com.afinco.backend.api.transaction.dto.PageResponse;
import com.afinco.backend.api.transaction.dto.TransactionCreateRequest;
import com.afinco.backend.api.transaction.dto.TransactionFilterRequest;
import com.afinco.backend.api.transaction.dto.TransactionMonthResponse;
import com.afinco.backend.api.transaction.dto.TransactionResponse;
import com.afinco.backend.service.TransactionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @GetMapping("/months")
    public List<TransactionMonthResponse> findMonths(
            @RequestParam(required = false) @Size(max = 100) String bankName) {
        return transactionService.findMonths(bankName);
    }

    @PostMapping({"", "/"})
    public ResponseEntity<TransactionResponse> create(
            @Valid @RequestBody TransactionCreateRequest request) {
        TransactionResponse transaction = transactionService.create(request);
        URI location = URI.create("/api/v1/transactions/" + transaction.id());
        return ResponseEntity.created(location).body(transaction);
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
