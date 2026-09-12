package com.afinco.backend.api.transaction.dto;

import com.afinco.backend.domain.ExpenseType;

public record CategoryResponse(Long id, String name, ExpenseType expenseType, String colorCode) {
}
