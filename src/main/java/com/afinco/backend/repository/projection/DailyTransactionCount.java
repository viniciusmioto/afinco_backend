package com.afinco.backend.repository.projection;

import java.time.LocalDate;

public interface DailyTransactionCount {

    LocalDate getDate();

    long getTransactionCount();
}
