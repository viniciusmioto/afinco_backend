package com.afinco.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.afinco.backend.api.transaction.dto.AccountResponse;
import com.afinco.backend.api.transaction.dto.CategoryResponse;
import com.afinco.backend.domain.Account;
import com.afinco.backend.domain.ExpenseType;
import com.afinco.backend.domain.Category;
import com.afinco.backend.mapper.AccountMapper;
import com.afinco.backend.mapper.CategoryMapper;
import com.afinco.backend.repository.AccountRepository;
import com.afinco.backend.repository.CategoryRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class DefaultReferenceDataServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private AccountMapper accountMapper;

    @Mock
    private CategoryMapper categoryMapper;

    private DefaultReferenceDataService service;

    @BeforeEach
    void setUp() {
        service = new DefaultReferenceDataService(
                accountRepository, categoryRepository, accountMapper, categoryMapper);
    }

    @Test
    void mapsSortedAccountsToResponses() {
        Account account = new Account("TD Bank", "2048", "CAD");
        when(accountRepository.findAll(any(Sort.class))).thenReturn(List.of(account));
        when(accountMapper.toResponse(account)).thenReturn(new AccountResponse(4L, "TD Bank", "2048", "CAD"));

        assertThat(service.findAccounts())
                .containsExactly(new AccountResponse(4L, "TD Bank", "2048", "CAD"));
    }

    @Test
    void mapsSortedCategoriesToResponses() {
        Category category = new Category("Groceries", ExpenseType.VARIABLE, "#2563EB");
        when(categoryRepository.findAll(any(Sort.class))).thenReturn(List.of(category));
        when(categoryMapper.toResponse(category)).thenReturn(new CategoryResponse(2L, "Groceries", ExpenseType.VARIABLE, "#2563EB"));

        assertThat(service.findCategories())
                .containsExactly(new CategoryResponse(2L, "Groceries", ExpenseType.VARIABLE, "#2563EB"));
    }

    @Test
    void returnsEmptyListsWhenNothingIsPersisted() {
        when(accountRepository.findAll(any(Sort.class))).thenReturn(List.of());
        when(categoryRepository.findAll(any(Sort.class))).thenReturn(List.of());

        assertThat(service.findAccounts()).isEmpty();
        assertThat(service.findCategories()).isEmpty();
    }
}
