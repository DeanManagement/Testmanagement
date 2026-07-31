package com.deanmanagement.testmanagement.project.internal.service;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Short-lived, single-report view sessions for Allure reports (PRD-018).
 *
 * The report is rendered in a sandboxed iframe with an opaque origin, which never sends
 * cookies or Authorization headers — so the view endpoint authenticates via an unguessable
 * token in the URL path instead ({@code /view/{token}/**}). Relative asset/data fetches
 * inside the report automatically stay under the token prefix. A token grants read-only
 * access to exactly one report for a limited time and is minted only after a normal
 * JWT + project-role check, so it is far less sensitive than the JWT it replaces.
 */
@Service
public class AllureViewSessionService {

    static final Duration TTL = Duration.ofMinutes(30);
    /** Hard cap so a misbehaving client cannot grow the map unboundedly. */
    private static final int MAX_SESSIONS = 10_000;

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public AllureViewSessionService(Clock clock) {
        this.clock = clock;
    }

    private record Session(String testRunKey, Instant expiresAt) {}

    /** Mints a view token for the given test run. Caller must have verified project access. */
    public String create(String testRunKey) {
        purgeExpired();
        if (sessions.size() >= MAX_SESSIONS) {
            sessions.clear(); // safety valve; worst case viewers re-open the report
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, new Session(testRunKey, clock.instant().plus(TTL)));
        return token;
    }

    /** @return true if the token exists, is unexpired, and was minted for this test run. */
    public boolean isValid(String token, String testRunKey) {
        Session session = sessions.get(token);
        if (session == null) {
            return false;
        }
        if (clock.instant().isAfter(session.expiresAt())) {
            sessions.remove(token);
            return false;
        }
        return session.testRunKey().equals(testRunKey);
    }

    private void purgeExpired() {
        Instant now = clock.instant();
        sessions.entrySet().removeIf(e -> now.isAfter(e.getValue().expiresAt()));
    }
}
