package com.afinco.backend.service;

import com.afinco.backend.api.transaction.dto.AccountCreateRequest;
import com.afinco.backend.api.transaction.dto.AccountResponse;
import com.afinco.backend.api.transaction.dto.CategoryResponse;
import com.afinco.backend.domain.Account;
import com.afinco.backend.mapper.AccountMapper;
import com.afinco.backend.mapper.CategoryMapper;
import com.afinco.backend.repository.AccountRepository;
import com.afinco.backend.repository.CategoryRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DefaultReferenceDataService implements ReferenceDataService {

    private static final Sort ACCOUNT_SORT = Sort.by(Sort.Order.asc("bankName"), Sort.Order.asc("id"));
    private static final Sort CATEGORY_SORT = Sort.by(Sort.Order.asc("name"));

    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final AccountMapper accountMapper;
    private final CategoryMapper categoryMapper;

    public DefaultReferenceDataService(
            AccountRepository accountRepository,
            CategoryRepository categoryRepository,
            AccountMapper accountMapper,
            CategoryMapper categoryMapper) {
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.accountMapper = accountMapper;
        this.categoryMapper = categoryMapper;
    }

    @Override
    public List<AccountResponse> findAccounts() {
        return accountRepository.findAll(ACCOUNT_SORT).stream().map(accountMapper::toResponse).toList();
    }

    @Override
    @Transactional
    public AccountResponse createAccount(AccountCreateRequest request) {
        Account account = new Account(request.bankName(), request.accountNumberLast4(), request.currency());
        return accountMapper.toResponse(accountRepository.save(account));
    }

    @Override
    public List<CategoryResponse> findCategories() {
        return categoryRepository.findAll(CATEGORY_SORT).stream().map(categoryMapper::toResponse).toList();
    }
}
