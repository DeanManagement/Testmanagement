package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.bugReport.BugReportResponse;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.ChangeBugStatusRequest;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.CreateBugReportRequest;
import com.deanmanagement.testmanagement.project.internal.dto.bugReport.UpdateBugReportRequest;
import com.deanmanagement.testmanagement.project.internal.service.BugReportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/bug-reports")
@Tag(name = "Bug Reports", description = "Bug report management endpoints")
@RequiredArgsConstructor
public class BugReportController {

    private final BugReportService bugReportService;

    @GetMapping
    public List<BugReportResponse> findAll(@PathVariable UUID projectId,
                                           @RequestParam(required = false) UUID testResultId) {
        if (testResultId != null) {
            return bugReportService.findByTestResult(projectId, testResultId);
        }
        return bugReportService.findByProject(projectId);
    }

    @GetMapping("/{id}")
    public BugReportResponse findById(@PathVariable UUID projectId, @PathVariable UUID id) {
        return bugReportService.findById(projectId, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BugReportResponse create(@PathVariable UUID projectId,
                                    @Valid @RequestBody CreateBugReportRequest request,
                                    Authentication authentication) {
        UUID userId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        return bugReportService.create(projectId, request, userId);
    }

    @PutMapping("/{id}")
    public BugReportResponse update(@PathVariable UUID projectId,
                                    @PathVariable UUID id,
                                    @Valid @RequestBody UpdateBugReportRequest request,
                                    Authentication authentication) {
        UUID userId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        return bugReportService.update(projectId, id, request, userId);
    }

    @PatchMapping("/{id}/status")
    public BugReportResponse changeStatus(@PathVariable UUID projectId,
                                          @PathVariable UUID id,
                                          @Valid @RequestBody ChangeBugStatusRequest request,
                                          Authentication authentication) {
        UUID userId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        return bugReportService.changeStatus(projectId, id, request, userId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID projectId, @PathVariable UUID id,
                       Authentication authentication) {
        UUID userId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        bugReportService.delete(projectId, id, userId);
    }
}
