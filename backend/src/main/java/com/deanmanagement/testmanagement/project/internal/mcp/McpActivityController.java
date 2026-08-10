package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.shared.PageableUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * What agents have been doing (PRD-025 §3.6). Instance-admin only, same guard as API-key
 * management — this is the page an admin opens when a project fills up with test cases nobody
 * remembers writing.
 */
@RestController
@RequestMapping("/api/mcp-activity")
@Tag(name = "MCP Activity", description = "Audit trail of MCP tool calls")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class McpActivityController {

    private final McpToolInvocationRepository invocationRepository;

    public record McpActivityResponse(UUID id, UUID apiKeyId, UUID projectId, String toolName,
                                      String outcome, String errorMessage, String arguments,
                                      String createdEntityType, UUID createdEntityId,
                                      long durationMs, Instant createdAt) {}

    @GetMapping
    public Page<McpActivityResponse> findAll(
            @RequestParam(required = false) UUID apiKeyId,
            @RequestParam(required = false) UUID projectId,
            Pageable pageable) {

        // createdAt DESC is baked into the queries, so normalise only clamps the page size here.
        Pageable normalized = PageableUtils.normalize(
                pageable.getSort().isSorted() ? pageable : pageableUnsorted(pageable));

        Page<McpToolInvocation> page;
        if (apiKeyId != null) {
            page = invocationRepository.findByApiKeyIdOrderByCreatedAtDesc(apiKeyId, normalized);
        } else if (projectId != null) {
            page = invocationRepository.findByProjectIdOrderByCreatedAtDesc(projectId, normalized);
        } else {
            page = invocationRepository.findAllByOrderByCreatedAtDesc(normalized);
        }
        return page.map(McpActivityController::toResponse);
    }

    private static Pageable pageableUnsorted(Pageable pageable) {
        return org.springframework.data.domain.PageRequest.of(
                Math.max(pageable.getPageNumber(), 0),
                pageable.getPageSize() <= 0 ? PageableUtils.DEFAULT_SIZE : pageable.getPageSize(),
                Sort.by(Sort.Order.desc("createdAt")));
    }

    private static McpActivityResponse toResponse(McpToolInvocation invocation) {
        return new McpActivityResponse(
                invocation.getId(),
                invocation.getApiKeyId(),
                invocation.getProjectId(),
                invocation.getToolName(),
                invocation.getOutcome(),
                invocation.getErrorMessage(),
                invocation.getArgumentsJson(),
                invocation.getCreatedEntityType(),
                invocation.getCreatedEntityId(),
                invocation.getDurationMs(),
                invocation.getCreatedAt());
    }
}
