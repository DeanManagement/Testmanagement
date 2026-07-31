package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import com.deanmanagement.testmanagement.project.internal.dto.version.TestCaseVersionResponse;
import com.deanmanagement.testmanagement.project.internal.dto.version.TestCaseVersionSummary;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseVersionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Test case history (PRD-011). Read-only by design: old versions are evidence, and editing or
 * restoring one in place would undermine the point of keeping them.
 */
@RestController
@RequestMapping("/api/projects/{projectId}/test-cases/{testCaseId}/versions")
@Tag(name = "Test Case Versions", description = "History of a test case")
@RequiredArgsConstructor
public class TestCaseVersionController {

    private final TestCaseVersionService versionService;

    @GetMapping
    @RequireProjectRole
    public List<TestCaseVersionSummary> list(@PathVariable UUID projectId,
                                             @PathVariable UUID testCaseId) {
        return versionService.list(projectId, testCaseId);
    }

    @GetMapping("/{versionNumber}")
    @RequireProjectRole
    public TestCaseVersionResponse get(@PathVariable UUID projectId,
                                       @PathVariable UUID testCaseId,
                                       @PathVariable int versionNumber) {
        return versionService.get(projectId, testCaseId, versionNumber);
    }
}
