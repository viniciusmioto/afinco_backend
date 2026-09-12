package com.afinco.backend.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginAttemptServiceTest {

    @Test
    void blocksAnAddressAfterFiveFailures() {
        LoginAttemptService service = new LoginAttemptService(
                Clock.fixed(Instant.parse("2026-09-12T12:00:00Z"), ZoneOffset.UTC));

        for (int attempt = 1; attempt < LoginAttemptService.MAX_FAILURES; attempt++) {
            assertThat(service.recordFailure("127.0.0.1")).isZero();
        }

        assertThat(service.recordFailure("127.0.0.1"))
                .isEqualTo(LoginAttemptService.ATTEMPT_WINDOW);
        assertThat(service.retryAfter("127.0.0.1"))
                .isEqualTo(LoginAttemptService.ATTEMPT_WINDOW);
    }

    @Test
    void successfulLoginClearsFailures() {
        LoginAttemptService service = new LoginAttemptService(
                Clock.fixed(Instant.parse("2026-09-12T12:00:00Z"), ZoneOffset.UTC));
        service.recordFailure("127.0.0.1");
        service.recordSuccess("127.0.0.1");

        assertThat(service.retryAfter("127.0.0.1")).isEqualTo(Duration.ZERO);
    }
}
