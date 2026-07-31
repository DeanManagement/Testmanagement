package com.deanmanagement.testmanagement.user.internal.services;

import com.deanmanagement.testmanagement.shared.exception.TooManyRequestsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory sliding-window login throttle (PRD-020). Counters are per lowercase email and
 * per remote IP; either tripping blocks the attempt with 429 + Retry-After until the oldest
 * failure slides out of the window. In-memory is intentional: single-instance deployment,
 * ~50 users — counters resetting on restart is an accepted trade-off.
 */
@Service
public class LoginThrottleService {

    private static final Logger log = LoggerFactory.getLogger(LoginThrottleService.class);

    static final Duration WINDOW = Duration.ofMinutes(15);
    /** Failures per email before lockout — protects a single account. */
    static final int MAX_PER_EMAIL = 5;
    /** Failures per IP before lockout — looser, so one shared office IP isn't bricked. */
    static final int MAX_PER_IP = 20;

    private final Clock clock;
    private final Map<String, Deque<Instant>> failuresByEmail = new ConcurrentHashMap<>();
    private final Map<String, Deque<Instant>> failuresByIp = new ConcurrentHashMap<>();

    public LoginThrottleService(Clock clock) {
        this.clock = clock;
    }

    /** @throws TooManyRequestsException if the email or IP is currently locked out. */
    public void checkAllowed(String email, String ip) {
        long retryEmail = retryAfterSeconds(failuresByEmail, key(email), MAX_PER_EMAIL);
        long retryIp = retryAfterSeconds(failuresByIp, ip, MAX_PER_IP);
        long retry = Math.max(retryEmail, retryIp);
        if (retry > 0) {
            log.warn("Login throttled for email={} ip={} (retry in {}s)", key(email), ip, retry);
            throw new TooManyRequestsException(
                    "Too many failed login attempts. Try again later.", retry);
        }
    }

    public void recordFailure(String email, String ip) {
        Instant now = clock.instant();
        record(failuresByEmail, key(email), now);
        record(failuresByIp, ip, now);
        log.warn("Failed login attempt for email={} ip={}", key(email), ip);
    }

    public void recordSuccess(String email, String ip) {
        failuresByEmail.remove(key(email));
        failuresByIp.remove(ip);
    }

    private String key(String email) {
        return email == null ? "" : email.toLowerCase();
    }

    private void record(Map<String, Deque<Instant>> map, String key, Instant now) {
        map.compute(key, (k, deque) -> {
            Deque<Instant> d = deque == null ? new ArrayDeque<>() : deque;
            synchronized (d) {
                prune(d, now);
                d.addLast(now);
            }
            return d;
        });
    }

    /** @return seconds until the oldest failure leaves the window, or 0 if not locked out. */
    private long retryAfterSeconds(Map<String, Deque<Instant>> map, String key, int max) {
        Deque<Instant> deque = map.get(key);
        if (deque == null) {
            return 0;
        }
        Instant now = clock.instant();
        synchronized (deque) {
            prune(deque, now);
            if (deque.size() < max) {
                return 0;
            }
            return Math.max(1, Duration.between(now, deque.peekFirst().plus(WINDOW)).getSeconds());
        }
    }

    private void prune(Deque<Instant> deque, Instant now) {
        Instant cutoff = now.minus(WINDOW);
        while (!deque.isEmpty() && deque.peekFirst().isBefore(cutoff)) {
            deque.removeFirst();
        }
    }
}
