package com.afinco.backend.statement;

import com.afinco.backend.domain.StatementType;
import com.afinco.backend.statement.dto.ParsedStatement;

public interface StatementParser {
    ParsedStatement parse(StatementDocument document);

    boolean supports(String documentText);

    String bankName();

    StatementType statementType();
}
