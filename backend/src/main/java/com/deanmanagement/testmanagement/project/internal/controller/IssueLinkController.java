package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import com.deanmanagement.testmanagement.project.internal.dto.issuetracker.CreateIssueLinkRequest;
import com.deanmanagement.testmanagement.project.internal.dto.issuetracker.IssueLinkResponse;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.service.IssueLinkService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Issues linked to a single test result (PRD-010 §3.3). Reading is open to any project member;
 * linking, creating and unlinking require TESTER, matching who is allowed to record results.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/test-runs/{runId}/results/{resultId}/issues")
@Tag(name = "Issue Links", description = "Issues linked to a test result")
@RequiredArgsConstructor
public class IssueLinkController {

    private final IssueLinkService issueLinkService;

    @GetMapping
    @RequireProjectRole
    public List<IssueLinkResponse> list(@PathVariable UUID projectId,
                                        @PathVariable UUID runId,
                                        @PathVariable UUID resultId) {
        return issueLinkService.findForResult(projectId, resultId);
    }

    /** On-demand state refresh, so opening a result does not show a pill stale by up to a poll cycle. */
    @PostMapping("/refresh")
    @RequireProjectRole
    public List<IssueLinkResponse> refresh(@PathVariable UUID projectId,
                                           @PathVariable UUID runId,
                                           @PathVariable UUID resultId) {
        return issueLinkService.refresh(projectId, resultId);
    }

    @PostMapping
    @RequireProjectRole(ProjectRole.TESTER)
    @ResponseStatus(HttpStatus.CREATED)
    public IssueLinkResponse link(@PathVariable UUID projectId,
                                  @PathVariable UUID runId,
                                  @PathVariable UUID resultId,
                                  @Valid @RequestBody CreateIssueLinkRequest request,
                                  Authentication authentication) {
        UUID actorId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        return issueLinkService.link(projectId, resultId, request, actorId);
    }

    @DeleteMapping("/{linkId}")
    @RequireProjectRole(ProjectRole.TESTER)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@PathVariable UUID projectId,
                       @PathVariable UUID runId,
                       @PathVariable UUID resultId,
                       @PathVariable UUID linkId,
                       Authentication authentication) {
        UUID actorId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        issueLinkService.unlink(projectId, resultId, linkId, actorId);
    }
}
