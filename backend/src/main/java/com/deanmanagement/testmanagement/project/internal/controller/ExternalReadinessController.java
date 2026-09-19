package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.access.ProjectAccessService;
import com.deanmanagement.testmanagement.project.internal.dto.readiness.ReadinessResponse;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.service.ExternalRefResolver;
import com.deanmanagement.testmanagement.project.internal.service.ReleaseReadinessService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The release gate for CI (PRD-037 §3.3). With {@code enforce=true} anything but GO is 412 with
 * the same body, so a pipeline step is just {@code curl --fail-with-body}. That includes NO_CRITERIA:
 * a pipeline that asks for a gate and finds none is misconfigured, and passing it would fail open.
 */
@RestController
@RequestMapping("/api/external/projects/{projectRef}/test-plans")
@Tag(name = "External Release Gate", description = "Release readiness of a test plan, for CI")
@RequiredArgsConstructor
public class ExternalReadinessController {

    private final ReleaseReadinessService readinessService;
    private final ExternalRefResolver refResolver;
    private final ProjectAccessService projectAccessService;

    @GetMapping("/{planId}/readiness")
    public ResponseEntity<ReadinessResponse> readiness(@PathVariable String projectRef, @PathVariable UUID planId,
                                                       @RequestParam(defaultValue = "false") boolean enforce) {
        // Checked here rather than with @RequireProjectRole, which can't resolve a project key; see
        // ExternalTestRunController.requireTester.
        Project project = refResolver.resolveProject(projectRef);
        projectAccessService.requireRoleForCurrentUser(project.getId(), ProjectRole.VIEWER);

        ReadinessResponse readiness = readinessService.readiness(project.getId(), planId);
        boolean blocked = enforce && readiness.verdict() != ReadinessResponse.Verdict.GO;
        return ResponseEntity.status(blocked ? HttpStatus.PRECONDITION_FAILED : HttpStatus.OK).body(readiness);
    }
}
