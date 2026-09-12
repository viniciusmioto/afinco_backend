package com.afinco.backend.api.transaction.dto;

import java.time.YearMonth;

public record TransactionMonthResponse(YearMonth month, long transactionCount) {
}
