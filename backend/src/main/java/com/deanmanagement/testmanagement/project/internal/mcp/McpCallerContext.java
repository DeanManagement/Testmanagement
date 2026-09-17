package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.access.ProjectAccessService;
import com.deanmanagement.testmanagement.project.internal.entity.ApiKey;
import com.deanmanagement.testmanagement.project.internal.entity.McpToolGroup;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;
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
    public record Caller(UUID projectId, String projectKey, UUID apiKeyId, UUID userId, ProjectRole role,
                         Set<McpToolGroup> toolGroups) {

        /** @param toolGroups null for an unrestricted key, which is every key issued before PRD-027 §9 */
        public Caller {
            toolGroups = toolGroups == null ? null : Set.copyOf(toolGroups);
        }

        public boolean isRestricted() {
            return toolGroups != null;
        }

        public boolean allows(McpToolGroup group) {
            return !isRestricted() || group == McpToolGroup.CORE || toolGroups.contains(group);
        }
    }

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
                userId, key.getRole(), key.getMcpToolGroups());
        // Handed to McpToolAuditor, which needs it but cannot resolve it itself — see McpCallerHolder.
        McpCallerHolder.set(caller);
        return caller;
    }

    /**
     * Resolves the caller and refuses a key that does not hold the tool's group (PRD-027 §9).
     *
     * <p>Called by {@link McpToolAuditor} around every tool rather than by the tools themselves, so
     * a new tool is covered without anyone remembering to ask. A tool nobody placed in a group is
     * refused to restricted keys: failing closed costs an administrator a confused minute, failing
     * open would make the restriction decorative.
     *
     * <p>This is the boundary. {@link McpToolListFilter} hides the same tools from
     * {@code tools/list}, but that only saves context — a client can call a tool it was never
     * shown.
     */
    @Transactional(readOnly = true)
    public Caller requireAllowed(String toolName, Optional<McpToolGroup> group) {
        Caller caller = require();
        if (!caller.isRestricted() || group.filter(caller::allows).isPresent()) {
            return caller;
        }
        throw new McpToolException("This API key is not allowed to use " + toolName
                + group.map(g -> ", which is in the " + g + " tool group").orElse("")
                + ". It holds " + caller.toolGroups() + " — ask an administrator to issue a key "
                + "that includes the group you need.");
    }

    /**
     * Resolves the caller for a write tool, refusing a VIEWER key.
     *
     * <p>Roles do not shape the tool list — a VIEWER key still sees the write tools, and so can
     * still <em>call</em> one. It gets this error, which names the role it would need, rather than
     * a confusing "unknown tool". What does shape the list is the key's tool groups: see
     * {@link #requireAllowed}.
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
