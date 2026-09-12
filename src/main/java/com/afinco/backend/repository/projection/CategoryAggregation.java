package com.afinco.backend.repository.projection;

import java.math.BigDecimal;

public interface CategoryAggregation {

    Long getCategoryId();

    String getCategoryName();

    String getColorCode();

    BigDecimal getTotalAmount();

    long getTransactionCount();
}
