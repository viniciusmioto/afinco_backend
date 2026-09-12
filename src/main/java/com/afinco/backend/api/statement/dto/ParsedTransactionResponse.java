package com.afinco.backend.api.statement.dto;

import com.afinco.backend.domain.ExpenseType;
import com.afinco.backend.domain.TransactionStatus;
import com.afinco.backend.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ParsedTransactionResponse(
        LocalDate date,
        BigDecimal amount,
        TransactionType type,
        String description,
        String bankName,
        String hashSignature,
        TransactionStatus status,
        boolean duplicate,
        ExpenseType expenseType,
        String categoryName) {
}
