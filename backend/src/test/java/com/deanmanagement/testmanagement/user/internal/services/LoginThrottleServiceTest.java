package com.deanmanagement.testmanagement.user.internal.services;

import com.deanmanagement.testmanagement.shared.exception.TooManyRequestsException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoginThrottleServiceTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-06-10T12:00:00Z");

        @Override public Instant instant() { return now; }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        void advance(Duration d) { now = now.plus(d); }
    }

    private final MutableClock clock = new MutableClock();
    private final LoginThrottleService service = new LoginThrottleService(clock);

    private void fail(String email, String ip, int times) {
        for (int i = 0; i < times; i++) {
            service.recordFailure(email, ip);
        }
    }

    @Test
    void locksEmailAfterMaxFailures() {
        fail("a@x.ch", "1.1.1.1", LoginThrottleService.MAX_PER_EMAIL);
        assertThatThrownBy(() -> service.checkAllowed("a@x.ch", "9.9.9.9"))
                .isInstanceOf(TooManyRequestsException.class);
        // Other accounts from other IPs unaffected.
        assertThatCode(() -> service.checkAllowed("b@x.ch", "9.9.9.9")).doesNotThrowAnyException();
    }

    @Test
    void emailKeyIsCaseInsensitive() {
        fail("A@X.ch", "1.1.1.1", LoginThrottleService.MAX_PER_EMAIL);
        assertThatThrownBy(() -> service.checkAllowed("a@x.ch", "2.2.2.2"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void locksIpAfterMaxFailures_acrossEmails() {
        for (int i = 0; i < LoginThrottleService.MAX_PER_IP; i++) {
            service.recordFailure("user" + i + "@x.ch", "1.1.1.1");
        }
        assertThatThrownBy(() -> service.checkAllowed("fresh@x.ch", "1.1.1.1"))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void successResetsCounters() {
        fail("a@x.ch", "1.1.1.1", LoginThrottleService.MAX_PER_EMAIL - 1);
        service.recordSuccess("a@x.ch", "1.1.1.1");
        fail("a@x.ch", "1.1.1.1", LoginThrottleService.MAX_PER_EMAIL - 1);
        assertThatCode(() -> service.checkAllowed("a@x.ch", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void lockoutExpiresWithWindow() {
        fail("a@x.ch", "1.1.1.1", LoginThrottleService.MAX_PER_EMAIL);
        clock.advance(LoginThrottleService.WINDOW.plusSeconds(1));
        assertThatCode(() -> service.checkAllowed("a@x.ch", "1.1.1.1")).doesNotThrowAnyException();
    }

    @Test
    void retryAfterIsReported() {
        fail("a@x.ch", "1.1.1.1", LoginThrottleService.MAX_PER_EMAIL);
        assertThatThrownBy(() -> service.checkAllowed("a@x.ch", "1.1.1.1"))
                .isInstanceOfSatisfying(TooManyRequestsException.class, e ->
                        org.assertj.core.api.Assertions.assertThat(e.getRetryAfterSeconds())
                                .isBetween(1L, LoginThrottleService.WINDOW.getSeconds()));
    }
}
