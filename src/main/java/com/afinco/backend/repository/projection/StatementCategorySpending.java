package com.afinco.backend.repository.projection;

import java.math.BigDecimal;

public interface StatementCategorySpending {

    Long getStatementId();

    Long getCategoryId();

    BigDecimal getTotalAmount();

    long getTransactionCount();
}
