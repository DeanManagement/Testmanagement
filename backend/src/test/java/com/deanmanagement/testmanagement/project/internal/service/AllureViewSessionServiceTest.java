package com.deanmanagement.testmanagement.project.internal.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AllureViewSessionServiceTest {

    /** Mutable clock so expiry is testable without sleeping. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-06-10T12:00:00Z");

        @Override public Instant instant() { return now; }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        void advance(Duration d) { now = now.plus(d); }
    }

    private final MutableClock clock = new MutableClock();
    private final AllureViewSessionService service = new AllureViewSessionService(clock);

    @Test
    void tokenIsValidForItsRunOnly() {
        String token = service.create("RUN-1");
        assertThat(service.isValid(token, "RUN-1")).isTrue();
        assertThat(service.isValid(token, "RUN-2")).isFalse();
        assertThat(service.isValid("unknown", "RUN-1")).isFalse();
    }

    @Test
    void tokenExpires() {
        String token = service.create("RUN-1");
        clock.advance(AllureViewSessionService.TTL.plusSeconds(1));
        assertThat(service.isValid(token, "RUN-1")).isFalse();
    }

    @Test
    void tokensAreUnguessablyLongAndUnique() {
        String a = service.create("RUN-1");
        String b = service.create("RUN-1");
        assertThat(a).isNotEqualTo(b);
        assertThat(a.length()).isGreaterThanOrEqualTo(40); // 32 random bytes, base64url
    }
}
