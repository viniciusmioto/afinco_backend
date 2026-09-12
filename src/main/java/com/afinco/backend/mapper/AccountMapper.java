package com.afinco.backend.mapper;

import com.afinco.backend.api.transaction.dto.AccountResponse;
import com.afinco.backend.domain.Account;
import org.springframework.stereotype.Component;

@Component
public class AccountMapper {

    public AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getBankName(),
                account.getAccountNumberLast4(),
                account.getCurrency());
    }
}
