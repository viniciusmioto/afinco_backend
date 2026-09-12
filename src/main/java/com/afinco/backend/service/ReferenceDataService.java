package com.afinco.backend.service;

import com.afinco.backend.api.transaction.dto.AccountCreateRequest;
import com.afinco.backend.api.transaction.dto.AccountResponse;
import com.afinco.backend.api.transaction.dto.CategoryResponse;
import java.util.List;

/**
 * Lookups the review screens need before they can build a transaction payload: the destination
 * account and the category choices offered for per-row overrides. Accounts can also be created so
 * a fresh database can receive its first import.
 */
public interface ReferenceDataService {

    List<AccountResponse> findAccounts();

    AccountResponse createAccount(AccountCreateRequest request);

    List<CategoryResponse> findCategories();
}
