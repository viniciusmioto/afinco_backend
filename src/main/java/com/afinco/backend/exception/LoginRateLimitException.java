package com.afinco.backend.exception;

import java.time.Duration;

public class LoginRateLimitException extends RuntimeException {

    private final Duration retryAfter;

    public LoginRateLimitException(Duration retryAfter) {
        super("Too many login attempts; try again later");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
