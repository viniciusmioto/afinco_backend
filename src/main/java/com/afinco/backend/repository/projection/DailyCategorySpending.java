package com.afinco.backend.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface DailyCategorySpending {

    LocalDate getDate();

    Long getCategoryId();

    BigDecimal getTotalAmount();

    long getTransactionCount();
}
