package com.deanmanagement.testmanagement.project.internal.mcp;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Writes the audit rows for {@link McpToolAuditor} (PRD-025 §3.6).
 *
 * <p>A separate bean on purpose: {@code REQUIRES_NEW} only takes effect through the Spring proxy,
 * and a private call inside the aspect would silently join the tool's transaction — so a refused
 * or failed call would roll its own audit row back, losing exactly the rows worth keeping.
 */
@Service
@RequiredArgsConstructor
public class McpInvocationRecorder {

    private static final Logger log = LoggerFactory.getLogger(McpInvocationRecorder.class);

    private final McpToolInvocationRepository invocationRepository;

    /**
     * Auditing must never break the call it is auditing, so failures here are logged and dropped.
     *
     * @param caller null when the call was refused before the caller could be resolved — an
     *               unauthenticated or revoked key. The row is still written; a rejected attempt is
     *               worth recording precisely because nobody owns it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(McpCallerContext.Caller caller, String toolName, String arguments,
                       String outcome, String errorMessage, String createdEntityType,
                       UUID createdEntityId, long durationMs) {
        try {
            McpToolInvocation invocation = new McpToolInvocation();
            invocation.setToolName(toolName);
            invocation.setArgumentsJson(arguments);
            invocation.setOutcome(outcome);
            invocation.setErrorMessage(errorMessage);
            invocation.setCreatedEntityType(createdEntityType);
            invocation.setCreatedEntityId(createdEntityId);
            invocation.setDurationMs(durationMs);
            if (caller != null) {
                invocation.setApiKeyId(caller.apiKeyId());
                invocation.setProjectId(caller.projectId());
                invocation.setServiceUserId(caller.userId());
            }
            invocationRepository.save(invocation);
        } catch (RuntimeException e) {
            log.warn("Could not record MCP invocation of {}: {}", toolName, e.getMessage());
        }
    }

    @Transactional
    public int purgeOlderThan(Instant cutoff) {
        return invocationRepository.deleteOlderThan(cutoff);
    }
}
