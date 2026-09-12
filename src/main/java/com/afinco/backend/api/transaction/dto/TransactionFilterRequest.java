package com.afinco.backend.api.transaction.dto;

import com.afinco.backend.domain.TransactionStatus;
import com.afinco.backend.domain.TransactionType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

public record TransactionFilterRequest(
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        @Positive Long statementId,
        @Positive Long accountId,
        @Positive Long categoryId,
        TransactionType type,
        TransactionStatus status,
        @Min(0) Integer page,
        @Min(1) @Max(MAX_SIZE) Integer size) {

    /** Large enough for one statement or one busy month in a single request. */
    public static final int MAX_SIZE = 500;

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 20;

    public int resolvedPage() {
        return page == null ? DEFAULT_PAGE : page;
    }

    public int resolvedSize() {
        return size == null ? DEFAULT_SIZE : size;
    }

    @AssertTrue(message = "startDate must not be after endDate")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !startDate.isAfter(endDate);
    }
}
