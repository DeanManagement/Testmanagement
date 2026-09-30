package com.deanmanagement.testmanagement.project.internal.entity;

import com.deanmanagement.testmanagement.shared.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A build server registered at instance level (PRD-024). Unlike issue trackers these are global:
 * the credential exists once, managed by a system administrator, and projects only ever see the
 * workflows assigned to them — never the server, its URL, or its token. A server may be limited to
 * some projects; only those can have its workflows assigned, by a system or project admin.
 *
 * <p>The API token is stored AES-GCM encrypted with the shared application key and is never
 * returned by any endpoint. Entirely opt-in: with no active config the tool makes no outbound
 * calls at all (air-gap safe).
 */
@Entity
@Table(name = "build_server_configs")
@Getter
@Setter
@NoArgsConstructor
public class BuildServerConfig extends BaseEntity {

    /** Display name chosen by the admin, e.g. "Company GitLab". Unique across the instance. */
    @Column(nullable = false, length = 100, unique = true)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private BuildServerProviderType provider;

    /**
     * API root of the server, e.g. {@code https://gitlab.example.com} or
     * {@code https://api.github.com}. Self-hosted instances are SSRF-validated before storage.
     */
    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    /** For Jenkins this holds {@code user:apiToken}; other providers store the bare token. */
    @Column(name = "api_token_encrypted", nullable = false, columnDefinition = "TEXT")
    private String apiTokenEncrypted;

    @Column(nullable = false)
    private boolean active = true;

    /**
     * Last failure from the provider. Set on auth or transport errors so the settings UI can flag
     * the server as needing attention, and cleared on the next success.
     */
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "last_error_at")
    private Instant lastErrorAt;

    /** Azure DevOps only (PRD-026): the {@code api-version} to send; null means the adapter's default. */
    @Column(name = "api_version", length = 10)
    private String apiVersion;

    /** False limits the server to {@link #projectIds}; an empty set then means no project at all. */
    @Column(name = "all_projects", nullable = false)
    private boolean allProjects = true;

    @ElementCollection
    @CollectionTable(name = "build_server_projects", joinColumns = @JoinColumn(name = "build_server_config_id"))
    @Column(name = "project_id", nullable = false)
    private Set<UUID> projectIds = new HashSet<>();
}
