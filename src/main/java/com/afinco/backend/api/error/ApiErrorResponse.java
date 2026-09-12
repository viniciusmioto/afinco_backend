package com.afinco.backend.api.error;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> validationErrors) {

    public ApiErrorResponse {
        validationErrors = validationErrors == null ? Map.of() : Map.copyOf(validationErrors);
    }
}
