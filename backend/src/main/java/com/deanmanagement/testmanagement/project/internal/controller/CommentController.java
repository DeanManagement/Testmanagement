package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import com.deanmanagement.testmanagement.project.internal.dto.comment.CommentResponse;
import com.deanmanagement.testmanagement.project.internal.dto.comment.CreateCommentRequest;
import com.deanmanagement.testmanagement.project.internal.dto.comment.UpdateCommentRequest;
import com.deanmanagement.testmanagement.project.internal.entity.CommentEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.service.CommentService;
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
@RequestMapping("/api/projects/{projectId}")
@Tag(name = "Comments", description = "Comment management endpoints")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping("/test-cases/{testCaseId}/comments")
    @RequireProjectRole
    public List<CommentResponse> getTestCaseComments(@PathVariable UUID projectId,
                                                     @PathVariable UUID testCaseId) {
        return commentService.findByEntity(projectId, CommentEntityType.TEST_CASE, testCaseId);
    }

    @PostMapping("/test-cases/{testCaseId}/comments")
    @RequireProjectRole(ProjectRole.TESTER)
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse createTestCaseComment(@PathVariable UUID projectId,
                                                 @PathVariable UUID testCaseId,
                                                 @Valid @RequestBody CreateCommentRequest request,
                                                 Authentication authentication) {
        UUID userId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        return commentService.create(projectId, CommentEntityType.TEST_CASE, testCaseId, request, userId);
    }

    @GetMapping("/test-runs/{runId}/results/{resultId}/comments")
    @RequireProjectRole
    public List<CommentResponse> getTestResultComments(@PathVariable UUID projectId,
                                                       @PathVariable UUID runId,
                                                       @PathVariable UUID resultId) {
        return commentService.findByEntity(projectId, CommentEntityType.TEST_RESULT, resultId);
    }

    @PostMapping("/test-runs/{runId}/results/{resultId}/comments")
    @RequireProjectRole(ProjectRole.TESTER)
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse createTestResultComment(@PathVariable UUID projectId,
                                                   @PathVariable UUID runId,
                                                   @PathVariable UUID resultId,
                                                   @Valid @RequestBody CreateCommentRequest request,
                                                   Authentication authentication) {
        UUID userId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        return commentService.create(projectId, CommentEntityType.TEST_RESULT, resultId, request, userId);
    }

    @PutMapping("/comments/{commentId}")
    @RequireProjectRole
    public CommentResponse updateComment(@PathVariable UUID projectId,
                                         @PathVariable UUID commentId,
                                         @Valid @RequestBody UpdateCommentRequest request,
                                         Authentication authentication) {
        UUID userId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        return commentService.update(projectId, commentId, request, userId);
    }

    @DeleteMapping("/comments/{commentId}")
    @RequireProjectRole
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable UUID projectId,
                              @PathVariable UUID commentId,
                              Authentication authentication) {
        UUID userId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        commentService.delete(projectId, commentId, userId);
    }
}
