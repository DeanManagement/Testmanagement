package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.shared.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * One MCP tool call (PRD-025 §3.6). Ids are stored loose rather than as associations: this is an
 * audit record and must outlive the things it points at.
 */
@Entity
@Table(name = "mcp_tool_invocations")
@Getter
@Setter
@NoArgsConstructor
public class McpToolInvocation extends BaseEntity {

    @Column(name = "api_key_id")
    private UUID apiKeyId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "service_user_id")
    private UUID serviceUserId;

    @Column(name = "tool_name", nullable = false, length = 100)
    private String toolName;

    @Column(name = "arguments_json", columnDefinition = "TEXT")
    private String argumentsJson;

    /** SUCCESS | REFUSED | ERROR. REFUSED is a guard doing its job; ERROR is something unexpected. */
    @Column(nullable = false, length = 20)
    private String outcome;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "created_entity_type", length = 50)
    private String createdEntityType;

    @Column(name = "created_entity_id")
    private UUID createdEntityId;

    @Column(name = "duration_ms", nullable = false)
    private long durationMs;
}
