package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * PRD-025 §3.5. Stops an agent quietly re-creating cases that already exist — the failure mode that
 * turns an MCP integration from useful into a mess someone cleans up by hand.
 *
 * <p>This does <em>not</em> reuse PRD-007's search, despite the surface similarity.
 * {@code SearchService} spans four entity types, caps at 50 hits, and its {@code SearchHit} exposes
 * no score at all — {@code ts_rank} orders the query and is then discarded — so there is nothing to
 * threshold against. Hence a small purpose-built check.
 *
 * <p>Matching is exact-after-normalisation, which works identically on PostgreSQL and H2 and
 * catches the common case: the same prompt run twice producing the same titles. A fuzzy tier would
 * need {@code pg_trgm}, which is not installed here and which a managed Postgres may not grant;
 * {@link McpProperties#isFuzzyDuplicates()} is where that would hang, off by default so the guard
 * degrades rather than fails.
 */
@Component
@RequiredArgsConstructor
public class TestCaseDuplicateDetector {

    private final TestCaseRepository testCaseRepository;

    /** An existing case that a proposed title would collide with. */
    public record Existing(UUID id, String key, String title) {}

    /**
     * A project's titles, loaded once. Bulk creates check up to {@code max-bulk-size} titles in one
     * call; re-querying per item would turn one tool call into 50 table scans.
     */
    public static final class Index {
        private final Map<String, Existing> byNormalisedTitle;

        private Index(Map<String, Existing> byNormalisedTitle) {
            this.byNormalisedTitle = byNormalisedTitle;
        }

        public Optional<Existing> find(String title) {
            String key = normalise(title);
            return key.isEmpty() ? Optional.empty() : Optional.ofNullable(byNormalisedTitle.get(key));
        }

        /** Lets a bulk call catch duplicates *within its own batch*, not just against the database. */
        public void remember(String title, Existing created) {
            String key = normalise(title);
            if (!key.isEmpty()) {
                byNormalisedTitle.putIfAbsent(key, created);
            }
        }
    }

    @Transactional(readOnly = true)
    public Index index(UUID projectId) {
        Map<String, Existing> byTitle = new HashMap<>();
        for (Object[] row : testCaseRepository.findTitlesByProjectId(projectId)) {
            String title = (String) row[2];
            String key = normalise(title);
            if (!key.isEmpty()) {
                byTitle.putIfAbsent(key, new Existing((UUID) row[0], (String) row[1], title));
            }
        }
        return new Index(byTitle);
    }

    @Transactional(readOnly = true)
    public Optional<Existing> findDuplicate(UUID projectId, String title) {
        return index(projectId).find(title);
    }

    /**
     * Lowercase, punctuation to spaces, whitespace collapsed. So "Login with valid credentials",
     * "login with valid credentials." and "Login  with  valid  credentials!" are one title — which
     * is how a human reading the list would see them.
     */
    static String normalise(String title) {
        if (title == null) {
            return "";
        }
        return title.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
