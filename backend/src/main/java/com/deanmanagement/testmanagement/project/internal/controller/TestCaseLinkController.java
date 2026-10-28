package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import com.deanmanagement.testmanagement.project.internal.dto.testCaseLink.CreateTestCaseLinkRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCaseLink.TestCaseLinkResponse;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseLinkService;
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

/** Links between test cases (TM07): any member reads, TESTER writes. */
@RestController
@RequestMapping("/api/projects/{projectId}/test-cases/{testCaseId}/links")
@Tag(name = "Test case links", description = "Precondition and related-case links")
@RequiredArgsConstructor
public class TestCaseLinkController {

    private final TestCaseLinkService linkService;

    @GetMapping
    @RequireProjectRole
    public List<TestCaseLinkResponse> list(@PathVariable UUID projectId, @PathVariable UUID testCaseId) {
        return linkService.list(projectId, testCaseId);
    }

    @PostMapping
    @RequireProjectRole(ProjectRole.TESTER)
    public List<TestCaseLinkResponse> link(@PathVariable UUID projectId, @PathVariable UUID testCaseId,
                                           @Valid @RequestBody CreateTestCaseLinkRequest request,
                                           Authentication authentication) {
        return linkService.link(projectId, testCaseId, request.targetTestCaseId(), request.linkType(),
                actor(authentication));
    }

    @DeleteMapping("/{linkId}")
    @RequireProjectRole(ProjectRole.TESTER)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unlink(@PathVariable UUID projectId, @PathVariable UUID testCaseId, @PathVariable UUID linkId,
                       Authentication authentication) {
        linkService.unlink(projectId, testCaseId, linkId, actor(authentication));
    }

    private static UUID actor(Authentication authentication) {
        return authentication != null ? UUID.fromString(authentication.getName()) : null;
    }
}
