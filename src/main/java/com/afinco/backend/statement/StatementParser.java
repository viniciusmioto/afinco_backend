package com.afinco.backend.statement;

import com.afinco.backend.statement.dto.ParsedTransactionDTO;
import java.io.InputStream;
import java.util.List;

public interface StatementParser {
    List<ParsedTransactionDTO> parse(InputStream stream);

    boolean supports(String documentText);

    String bankName();

    StatementType statementType();
}
