package com.afinco.backend.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final Duration ATTEMPT_WINDOW = Duration.ofMinutes(15);

    private final ConcurrentMap<String, Attempt> attempts = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginAttemptService() {
        this(Clock.systemUTC());
    }

    LoginAttemptService(Clock clock) {
        this.clock = clock;
    }

    public Duration retryAfter(String clientAddress) {
        Instant now = clock.instant();
        Attempt attempt = attempts.get(clientAddress);
        if (attempt == null) {
            return Duration.ZERO;
        }
        if (!attempt.expiresAt().isAfter(now)) {
            attempts.remove(clientAddress, attempt);
            return Duration.ZERO;
        }
        return attempt.failures() >= MAX_FAILURES
                ? Duration.between(now, attempt.expiresAt())
                : Duration.ZERO;
    }

    public Duration recordFailure(String clientAddress) {
        Instant now = clock.instant();
        Attempt attempt = attempts.compute(clientAddress, (key, current) -> {
            if (current == null || !current.expiresAt().isAfter(now)) {
                return new Attempt(1, now.plus(ATTEMPT_WINDOW));
            }
            return new Attempt(current.failures() + 1, current.expiresAt());
        });
        return attempt.failures() >= MAX_FAILURES
                ? Duration.between(now, attempt.expiresAt())
                : Duration.ZERO;
    }

    public void recordSuccess(String clientAddress) {
        attempts.remove(clientAddress);
    }

    private record Attempt(int failures, Instant expiresAt) {
    }
}
