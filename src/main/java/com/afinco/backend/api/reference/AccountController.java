package com.afinco.backend.api.reference;

import com.afinco.backend.api.transaction.dto.AccountCreateRequest;
import com.afinco.backend.api.transaction.dto.AccountResponse;
import com.afinco.backend.service.ReferenceDataService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final ReferenceDataService referenceDataService;

    public AccountController(ReferenceDataService referenceDataService) {
        this.referenceDataService = referenceDataService;
    }

    @GetMapping({"", "/"})
    public List<AccountResponse> findAccounts() {
        return referenceDataService.findAccounts();
    }

    @PostMapping({"", "/"})
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody AccountCreateRequest request) {
        AccountResponse account = referenceDataService.createAccount(request);
        return ResponseEntity.created(URI.create("/api/v1/accounts/" + account.id())).body(account);
    }
}
