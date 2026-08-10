package com.deanmanagement.testmanagement.project.internal.mcp;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-key write budget for the MCP tools (PRD-025 §3.6).
 *
 * <p>Not a security control — the key's project role is that. This is a blast-radius limit for the
 * ordinary accident: an agent in a retry loop, or one that decides the right move is to write four
 * hundred test cases. Exceeding it surfaces as a tool error the agent can read and back off from,
 * rather than a transport failure it can only give up on.
 *
 * <p>Same sliding-window shape as PRD-020's {@code LoginThrottleService}, deliberately not the same
 * bean: that one lives in {@code user.internal.services}, which this module may not reach under
 * Spring Modulith, and its API is email/IP-shaped with hard-coded constants. Thirty lines of
 * duplication beats a premature extraction.
 */
@Component
@RequiredArgsConstructor
public class McpWriteThrottle {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final McpProperties properties;
    private final Map<UUID, Deque<Instant>> writesByKey = new ConcurrentHashMap<>();

    public void recordWrite(UUID apiKeyId) {
        recordWrites(apiKeyId, 1);
    }

    /**
     * @throws McpToolException if the budget is exhausted; nothing is recorded in that case, so a
     *                          well-behaved agent that waits is not penalised for having asked
     */
    public void recordWrites(UUID apiKeyId, int count) {
        int limit = properties.getMaxWritesPerMinute();
        if (limit <= 0) {
            return;
        }
        Instant now = Instant.now();
        Instant cutoff = now.minus(WINDOW);
        evictIdleKeys(cutoff);

        Deque<Instant> window = writesByKey.computeIfAbsent(apiKeyId, k -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
                window.pollFirst();
            }
            if (window.size() + count > limit) {
                throw new McpToolException("Write budget exhausted: at most " + limit
                        + " writes per minute for this API key, and this call needs " + count
                        + ". Wait a minute before writing again, or batch fewer items.");
            }
            for (int i = 0; i < count; i++) {
                window.addLast(now);
            }
        }
    }

    /**
     * Drops keys with nothing left in their window. Without this, every key ever used holds its
     * last minute of timestamps for the lifetime of the process, because a deque is only pruned
     * when that same key writes again — and a revoked key never does.
     *
     * <p>{@code remove(key, value)} under the deque's own monitor, so a writer that is mid-call on
     * the same deque cannot have its record silently dropped by the sweep.
     */
    private void evictIdleKeys(Instant cutoff) {
        writesByKey.forEach((key, window) -> {
            synchronized (window) {
                while (!window.isEmpty() && window.peekFirst().isBefore(cutoff)) {
                    window.pollFirst();
                }
                if (window.isEmpty()) {
                    writesByKey.remove(key, window);
                }
            }
        });
    }

    /** Test seam — the window is process-local, so a test that exhausts it must be able to reset. */
    void reset() {
        writesByKey.clear();
    }
}
