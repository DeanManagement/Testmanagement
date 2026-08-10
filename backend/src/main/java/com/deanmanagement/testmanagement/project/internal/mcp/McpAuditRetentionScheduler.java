package com.deanmanagement.testmanagement.project.internal.mcp;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * Keeps {@code mcp_tool_invocations} from growing without bound (PRD-025 §3.6). An agent can
 * generate audit rows far faster than a human can, so retention is not optional here.
 *
 * <p>Runs daily and does nothing when MCP is off — no config, no work, matching the air-gap
 * discipline of PRD-010 and PRD-024's pollers.
 */
@Component
@RequiredArgsConstructor
public class McpAuditRetentionScheduler {

    private static final Logger log = LoggerFactory.getLogger(McpAuditRetentionScheduler.class);

    private final McpProperties properties;
    private final McpInvocationRecorder recorder;

    @Scheduled(fixedDelayString = "${app.mcp.audit-purge-interval-ms:86400000}",
            initialDelayString = "${app.mcp.audit-purge-initial-delay-ms:3600000}")
    public void purge() {
        if (!properties.isEnabled() || properties.getAuditRetentionDays() <= 0) {
            return;
        }
        Instant cutoff = Instant.now().minus(Duration.ofDays(properties.getAuditRetentionDays()));
        int deleted = recorder.purgeOlderThan(cutoff);
        if (deleted > 0) {
            log.info("Purged {} MCP invocation record(s) older than {} days",
                    deleted, properties.getAuditRetentionDays());
        }
    }
}
