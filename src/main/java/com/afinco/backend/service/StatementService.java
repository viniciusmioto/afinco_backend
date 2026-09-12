package com.afinco.backend.service;

import com.afinco.backend.api.statement.dto.StatementImportRequest;
import com.afinco.backend.api.statement.dto.StatementImportResponse;
import com.afinco.backend.api.statement.dto.StatementResponse;
import java.util.List;

public interface StatementService {

    /** All imported statements, newest billing period first. */
    List<StatementResponse> findStatements();

    /**
     * Stores a reviewed statement and its kept rows atomically. Rows are appended when a statement with
     * the same account, type, and period already exists, so partial re-imports never split a period.
     */
    StatementImportResponse importStatement(StatementImportRequest request);
}
