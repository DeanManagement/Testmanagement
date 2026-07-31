package com.deanmanagement.testmanagement.project.internal.controller;
import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;

import com.deanmanagement.testmanagement.project.internal.dto.testCaseFolder.CreateTestCaseFolderRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCaseFolder.MoveTestCasesRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCaseFolder.ReorderFoldersRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCaseFolder.TestCaseFolderResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testCaseFolder.UpdateTestCaseFolderRequest;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseFolderService;
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
@RequestMapping("/api/projects/{projectId}/test-case-folders")
@Tag(name = "Test Case Folders", description = "Test case folder management endpoints")
@RequiredArgsConstructor
public class TestCaseFolderController {

    private final TestCaseFolderService folderService;

    @GetMapping
    @RequireProjectRole
    public List<TestCaseFolderResponse> getTree(@PathVariable UUID projectId) {
        return folderService.getTree(projectId);
    }

    @PostMapping
    @RequireProjectRole(ProjectRole.TESTER)
    @ResponseStatus(HttpStatus.CREATED)
    public TestCaseFolderResponse create(@PathVariable UUID projectId,
                                         @Valid @RequestBody CreateTestCaseFolderRequest request,
                                         Authentication authentication) {
        UUID userId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        return folderService.create(projectId, request, userId);
    }

    @PutMapping("/{folderId}")
    @RequireProjectRole(ProjectRole.TESTER)
    public TestCaseFolderResponse update(@PathVariable UUID projectId,
                                         @PathVariable UUID folderId,
                                         @Valid @RequestBody UpdateTestCaseFolderRequest request,
                                         Authentication authentication) {
        UUID userId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        return folderService.update(projectId, folderId, request, userId);
    }

    @DeleteMapping("/{folderId}")
    @RequireProjectRole(ProjectRole.TESTER)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID projectId,
                       @PathVariable UUID folderId,
                       Authentication authentication) {
        UUID userId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        folderService.delete(projectId, folderId, userId);
    }

    @PutMapping("/reorder")
    @RequireProjectRole(ProjectRole.TESTER)
    public List<TestCaseFolderResponse> reorder(@PathVariable UUID projectId,
                                                 @Valid @RequestBody ReorderFoldersRequest request,
                                                 Authentication authentication) {
        UUID userId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        return folderService.reorder(projectId, request, userId);
    }

    @PostMapping("/move-test-cases")
    @RequireProjectRole(ProjectRole.TESTER)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void moveTestCases(@PathVariable UUID projectId,
                              @Valid @RequestBody MoveTestCasesRequest request,
                              Authentication authentication) {
        UUID userId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        folderService.moveTestCases(projectId, request, userId);
    }
}
