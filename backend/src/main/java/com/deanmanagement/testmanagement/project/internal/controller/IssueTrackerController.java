package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import com.deanmanagement.testmanagement.project.internal.dto.issuetracker.IssueSearchResponse;
import com.deanmanagement.testmanagement.project.internal.dto.issuetracker.IssueTrackerConfigResponse;
import com.deanmanagement.testmanagement.project.internal.dto.issuetracker.SaveIssueTrackerConfigRequest;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerProviderType;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.issuetracker.IssueTrackerProviderRegistry;
import com.deanmanagement.testmanagement.project.internal.service.IssueLinkService;
import com.deanmanagement.testmanagement.project.internal.service.IssueTrackerConfigService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Per-project issue-tracker configuration and issue search (PRD-010 §3.3). Configuration is
 * admin-only; search is available to any member, since testers need it to link issues.
 */
@RestController
@RequestMapping("/api/projects/{projectId}")
@Tag(name = "Issue Tracker", description = "Issue tracker configuration and issue search")
@RequiredArgsConstructor
public class IssueTrackerController {

    private final IssueTrackerConfigService configService;
    private final IssueLinkService issueLinkService;
    private final IssueTrackerProviderRegistry providerRegistry;

    /** Providers with an adapter available, so the UI does not offer one that cannot work. */
    @GetMapping("/issue-tracker/providers")
    @RequireProjectRole(ProjectRole.ADMIN)
    public Set<IssueTrackerProviderType> supportedProviders(@PathVariable UUID projectId) {
        return providerRegistry.supported();
    }

    /** 204 rather than 404 when unset — "no tracker configured" is a normal state, not an error. */
    @GetMapping("/issue-tracker")
    @RequireProjectRole(ProjectRole.ADMIN)
    public ResponseEntity<IssueTrackerConfigResponse> get(@PathVariable UUID projectId) {
        return configService.find(projectId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/issue-tracker")
    @RequireProjectRole(ProjectRole.ADMIN)
    public IssueTrackerConfigResponse save(@PathVariable UUID projectId,
                                           @Valid @RequestBody SaveIssueTrackerConfigRequest request) {
        return configService.save(projectId, request);
    }

    @DeleteMapping("/issue-tracker")
    @RequireProjectRole(ProjectRole.ADMIN)
    public ResponseEntity<Void> delete(@PathVariable UUID projectId) {
        configService.delete(projectId);
        return ResponseEntity.noContent().build();
    }

    /** Verifies the stored credentials against the provider. 502 if the tracker rejects us. */
    @PostMapping("/issue-tracker/test")
    @RequireProjectRole(ProjectRole.ADMIN)
    public ResponseEntity<Void> testConnection(@PathVariable UUID projectId) {
        configService.testConnection(projectId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/issues/search")
    @RequireProjectRole
    public List<IssueSearchResponse> search(@PathVariable UUID projectId,
                                            @RequestParam(name = "q", required = false) String query) {
        return issueLinkService.search(projectId, query);
    }
}
