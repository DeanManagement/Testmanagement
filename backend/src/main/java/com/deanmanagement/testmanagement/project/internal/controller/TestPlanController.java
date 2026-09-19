package com.deanmanagement.testmanagement.project.internal.controller;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.BugReportResponse;
import com.deanmanagement.testmanagement.project.internal.service.BugReportLinkService;
import com.deanmanagement.testmanagement.project.internal.dto.effort.BurnDownResponse;
import com.deanmanagement.testmanagement.project.internal.dto.readiness.ReadinessResponse;
import com.deanmanagement.testmanagement.project.internal.service.ReleaseReadinessService;
import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;

import com.deanmanagement.testmanagement.project.internal.dto.testplan.CreateTestPlanRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.TestPlanResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.TestPlanSummaryResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.UpdateTestPlanRequest;
import com.deanmanagement.testmanagement.project.internal.service.TestPlanService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/test-plans")
@Tag(name = "Test Plans", description = "Test plan management endpoints")
@RequiredArgsConstructor
public class TestPlanController {

    private final TestPlanService testPlanService;
    private final ReleaseReadinessService readinessService;
    private final BugReportLinkService bugReportLinkService;

    @GetMapping
    @RequireProjectRole
    public List<TestPlanResponse> findAll(@PathVariable UUID projectId) {
        return testPlanService.findByProject(projectId);
    }

    @GetMapping("/{id}")
    @RequireProjectRole
    public TestPlanResponse findById(@PathVariable UUID projectId, @PathVariable UUID id) {
        return testPlanService.findById(projectId, id);
    }

    /** PRD-047: bugs found in, or linked to, the plan's runs. */
    @GetMapping("/{id}/bug-reports")
    @RequireProjectRole
    public List<BugReportResponse> getDefects(@PathVariable UUID projectId, @PathVariable UUID id) {
        return bugReportLinkService.findByTestPlan(projectId, id);
    }

    /** GO / NO_GO against the plan's release gate, with each criterion's value (PRD-037). */
    @GetMapping("/{id}/readiness")
    @RequireProjectRole
    public ReadinessResponse getReadiness(@PathVariable UUID projectId, @PathVariable UUID id) {
        return readinessService.readiness(projectId, id);
    }

    /** Remaining estimated effort per day (PRD-036). */
    @GetMapping("/{id}/burn-down")
    @RequireProjectRole
    public BurnDownResponse getBurnDown(@PathVariable UUID projectId, @PathVariable UUID id) {
        return testPlanService.getBurnDown(projectId, id);
    }

    @GetMapping("/{id}/summary")
    @RequireProjectRole
    public TestPlanSummaryResponse getSummary(@PathVariable UUID projectId, @PathVariable UUID id) {
        return testPlanService.getSummary(projectId, id);
    }

    @PostMapping
    @RequireProjectRole(ProjectRole.TESTER)
    @ResponseStatus(HttpStatus.CREATED)
    public TestPlanResponse create(@PathVariable UUID projectId,
                                   @Valid @RequestBody CreateTestPlanRequest request,
                                   Authentication authentication) {
        UUID currentUserId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        return testPlanService.create(projectId, request, currentUserId);
    }

    @PutMapping("/{id}")
    @RequireProjectRole(ProjectRole.TESTER)
    public TestPlanResponse update(@PathVariable UUID projectId,
                                   @PathVariable UUID id,
                                   @Valid @RequestBody UpdateTestPlanRequest request,
                                   Authentication authentication) {
        UUID currentUserId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        return testPlanService.update(projectId, id, request, currentUserId);
    }

    @DeleteMapping("/{id}")
    @RequireProjectRole(ProjectRole.TESTER)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID projectId, @PathVariable UUID id,
                       Authentication authentication) {
        UUID currentUserId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        testPlanService.delete(projectId, id, currentUserId);
    }
}
