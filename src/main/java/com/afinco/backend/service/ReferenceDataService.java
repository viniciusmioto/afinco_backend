package com.afinco.backend.service;

import com.afinco.backend.api.transaction.dto.AccountResponse;
import com.afinco.backend.api.transaction.dto.CategoryResponse;
import java.util.List;

/**
 * Read-only lookups the review screens need before they can build a transaction payload:
 * the destination account and the category choices offered for per-row overrides.
 */
public interface ReferenceDataService {

    List<AccountResponse> findAccounts();

    List<CategoryResponse> findCategories();
}
