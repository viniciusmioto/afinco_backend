package com.afinco.backend.api.reference;

import com.afinco.backend.api.transaction.dto.AccountResponse;
import com.afinco.backend.service.ReferenceDataService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
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
}
