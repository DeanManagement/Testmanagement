package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import com.deanmanagement.testmanagement.project.internal.dto.testCasePermission.GrantTestCasePermissionRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCasePermission.TestCasePermissionResponse;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.service.TestCasePermissionService;
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

@RestController
@RequestMapping("/api/projects/{projectId}/test-cases/{testCaseId}/permissions")
@Tag(name = "Test Case Permissions", description = "Grant and revoke per-user rights on a single test case")
@RequiredArgsConstructor
public class TestCasePermissionController {

    private final TestCasePermissionService permissionService;

    @GetMapping
    @RequireProjectRole
    public List<TestCasePermissionResponse> list(@PathVariable UUID projectId,
                                                 @PathVariable UUID testCaseId) {
        return permissionService.listForTestCase(projectId, testCaseId);
    }

    @PostMapping
    @RequireProjectRole(ProjectRole.ADMIN)
    @ResponseStatus(HttpStatus.CREATED)
    public TestCasePermissionResponse grant(@PathVariable UUID projectId,
                                            @PathVariable UUID testCaseId,
                                            @Valid @RequestBody GrantTestCasePermissionRequest request,
                                            Authentication authentication) {
        UUID actorId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        return permissionService.grant(projectId, testCaseId, request, actorId);
    }

    @DeleteMapping("/{permissionId}")
    @RequireProjectRole(ProjectRole.ADMIN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@PathVariable UUID projectId,
                       @PathVariable UUID testCaseId,
                       @PathVariable UUID permissionId,
                       Authentication authentication) {
        UUID actorId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        permissionService.revoke(projectId, testCaseId, permissionId, actorId);
    }
}
