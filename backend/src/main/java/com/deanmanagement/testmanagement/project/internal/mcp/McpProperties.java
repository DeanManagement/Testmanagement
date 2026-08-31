package com.deanmanagement.testmanagement.project.internal.mcp;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * PRD-025. Limits on what an agent may do through the MCP surface.
 *
 * <p>These are guardrails against an agent looping, not a security boundary — that is the API key's
 * project role. They exist because "wrote 4000 test cases overnight" is a plausible accident.
 */
@Component
@ConfigurationProperties(prefix = "app.mcp")
@Getter
@Setter
public class McpProperties {

    /** Master switch. Off means the endpoint does not exist at all — 404, not 401. */
    private boolean enabled = false;

    /**
     * Raised from 60 for PRD-027's execution tools. Authoring is human-paced; executing a run is
     * one write per test case, so a 50-case run is 52 writes and the old default was tripped
     * mid-run — leaving a half-recorded run, which is worse than a refusal. 120 still bounds an
     * overnight accident to something a human notices.
     */
    private int maxWritesPerMinute = 120;

    /** Cap on {@code create_test_cases_bulk}; also the cap on a single agent turn's blast radius. */
    private int maxBulkSize = 50;

    private int maxStepsPerCase = 100;

    private int auditRetentionDays = 90;

    /**
     * Trigram similarity above which a title counts as a duplicate. Only consulted when
     * {@link #fuzzyDuplicates} is on; exact normalised-title matching always applies.
     */
    private double duplicateThreshold = 0.85;

    /**
     * Needs {@code CREATE EXTENSION pg_trgm}, which a managed Postgres may not grant, and has no
     * H2 equivalent. Off by default so the guard degrades to exact matching rather than failing.
     */
    private boolean fuzzyDuplicates = false;
}
