package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import com.deanmanagement.testmanagement.project.internal.dto.requirement.RequirementSyncConfigResponse;
import com.deanmanagement.testmanagement.project.internal.dto.requirement.RequirementSyncResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.requirement.RequirementSyncStatusResponse;
import com.deanmanagement.testmanagement.project.internal.dto.requirement.SaveRequirementSyncConfigRequest;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.service.RequirementSyncService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Requirement sync from Azure DevOps (TM19). Settings and running a sync are admin-only; whether
 * sync is on, and how it last went, any member may see.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/requirement-sync")
@Tag(name = "Requirement Sync", description = "One-way requirement sync from Azure DevOps")
@RequiredArgsConstructor
public class RequirementSyncController {

    private final RequirementSyncService syncService;

    /** 204 when the project has no sync settings yet. */
    @GetMapping
    @RequireProjectRole(ProjectRole.ADMIN)
    public ResponseEntity<RequirementSyncConfigResponse> get(@PathVariable UUID projectId) {
        return syncService.find(projectId).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping
    @RequireProjectRole(ProjectRole.ADMIN)
    public RequirementSyncConfigResponse save(@PathVariable UUID projectId,
                                              @Valid @RequestBody SaveRequirementSyncConfigRequest request) {
        return syncService.save(projectId, request);
    }

    @GetMapping("/status")
    @RequireProjectRole
    public RequirementSyncStatusResponse status(@PathVariable UUID projectId) {
        return syncService.status(projectId);
    }

    /** 424 with the tracker's reason when Azure DevOps fails or refuses. */
    @PostMapping("/run")
    @RequireProjectRole(ProjectRole.ADMIN)
    public RequirementSyncResultResponse run(@PathVariable UUID projectId,
                                             @RequestParam(defaultValue = "false") boolean dryRun,
                                             Authentication authentication) {
        return syncService.sync(projectId, dryRun,
                authentication != null ? UUID.fromString(authentication.getName()) : null);
    }
}
