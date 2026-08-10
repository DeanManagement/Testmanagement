package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.access.ProjectAccessService;
import com.deanmanagement.testmanagement.project.internal.entity.ApiKey;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Resolves which project the calling API key may act on, and enforces its role.
 *
 * <p>This is why no MCP tool takes a project id: the key <em>is</em> the scope. A tool cannot be
 * tricked into naming another project because there is no parameter to name one with, and the role
 * check runs against the key's own {@code ProjectMember} row (PRD-025 §3.2).
 */
@Component
@RequiredArgsConstructor
public class McpCallerContext {

    private final ApiKeyRepository apiKeyRepository;
    private final ProjectAccessService projectAccessService;

    /**
     * @param projectId  the only project this caller can see
     * @param projectKey human-readable key, used in tool responses so an agent can quote it
     * @param apiKeyId   for the audit log
     * @param userId     the service user, used as the actor on anything written
     */
    public record Caller(UUID projectId, String projectKey, UUID apiKeyId, UUID userId, ProjectRole role) {}

    /** Resolves the caller for a read tool. */
    @Transactional(readOnly = true)
    public Caller require() {
        UUID userId = projectAccessService.resolvedCallerOrNull();
        if (userId == null) {
            throw new McpToolException("This tool requires an API key. Send X-API-Key or "
                    + "Authorization: Bearer tm_….");
        }
        ApiKey key = apiKeyRepository.findByServiceUserIdAndRevokedFalse(userId)
                .orElseThrow(() -> new McpToolException(
                        "This session is not backed by an active API key."));
        if (key.getProject() == null) {
            throw new McpToolException("This API key is not scoped to a project, so it cannot be "
                    + "used with the MCP tools. Re-create it from the admin settings.");
        }
        Caller caller = new Caller(key.getProject().getId(), key.getProject().getKey(), key.getId(),
                userId, key.getRole());
        // Handed to McpToolAuditor, which needs it but cannot resolve it itself — see McpCallerHolder.
        McpCallerHolder.set(caller);
        return caller;
    }

    /**
     * Resolves the caller for a write tool, refusing a VIEWER key.
     *
     * <p>The tool list is deliberately static — every key sees every tool — so a VIEWER key can
     * still <em>call</em> a write tool. It gets this error, which names the role it would need,
     * rather than a confusing "unknown tool". Filtering the advertised list per key was rejected:
     * it makes the tool list vary by caller and breaks client-side caching for little gain.
     */
    @Transactional(readOnly = true)
    public Caller requireWriter() {
        Caller caller = require();
        if (!caller.role().satisfies(ProjectRole.TESTER)) {
            throw new McpToolException("This API key holds " + caller.role() + " on project "
                    + caller.projectKey() + ". Writing requires TESTER — ask an administrator to "
                    + "re-create the key with that role.");
        }
        return caller;
    }
}
