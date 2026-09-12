package com.afinco.backend.repository.projection;

public interface StatementTransactionCount {

    Long getStatementId();

    long getTransactionCount();
}
