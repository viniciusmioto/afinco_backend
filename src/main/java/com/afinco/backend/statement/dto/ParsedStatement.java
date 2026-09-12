package com.afinco.backend.statement.dto;

import com.afinco.backend.domain.StatementPeriod;
import com.afinco.backend.domain.StatementType;
import java.util.List;

public record ParsedStatement(
        String bankName,
        StatementType statementType,
        StatementPeriod period,
        List<ParsedTransactionDTO> transactions) {

    public ParsedStatement {
        transactions = List.copyOf(transactions);
    }
}
