package com.afinco.backend.statement.dto;

import com.afinco.backend.domain.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ParsedTransactionDTO(
        LocalDate date, BigDecimal amount, TransactionType type, String description, String bankName) {
}
