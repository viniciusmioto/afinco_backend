package com.afinco.backend.mapper;

import com.afinco.backend.api.statement.dto.StatementResponse;
import com.afinco.backend.api.statement.dto.StatementSummaryResponse;
import com.afinco.backend.domain.Statement;
import org.springframework.stereotype.Component;

@Component
public class StatementMapper {

    private final AccountMapper accountMapper;

    public StatementMapper(AccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    public StatementResponse toResponse(Statement statement, long transactionCount) {
        return new StatementResponse(
                statement.getId(),
                accountMapper.toResponse(statement.getAccount()),
                statement.getStatementType(),
                statement.getPeriod().startDate(),
                statement.getPeriod().endDate(),
                transactionCount,
                statement.getCreatedAt());
    }

    /** Returns null for manual transactions, which have no statement. */
    public StatementSummaryResponse toSummary(Statement statement) {
        if (statement == null) {
            return null;
        }
        return new StatementSummaryResponse(
                statement.getId(),
                statement.getStatementType(),
                statement.getPeriod().startDate(),
                statement.getPeriod().endDate());
    }
}
