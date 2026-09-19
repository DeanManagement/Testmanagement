package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.comment.CommentResponse;
import com.deanmanagement.testmanagement.project.internal.dto.comment.CreateCommentRequest;
import com.deanmanagement.testmanagement.project.internal.dto.comment.UpdateCommentRequest;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.Comment;
import com.deanmanagement.testmanagement.project.internal.entity.CommentEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.repository.CommentRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestResultRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestResultRepository testResultRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserService userService;
    private final AuditService auditService;

    public List<CommentResponse> findByEntity(UUID projectId, CommentEntityType entityType, UUID entityId) {
        validateEntityBelongsToProject(projectId, entityType, entityId);
        return commentRepository.findByEntityTypeAndEntityIdOrderByCreatedAtAsc(entityType, entityId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public CommentResponse create(UUID projectId, CommentEntityType entityType, UUID entityId,
                                  CreateCommentRequest request, UUID userId) {
        validateEntityBelongsToProject(projectId, entityType, entityId);

        Comment comment = new Comment();
        comment.setContent(request.content());
        comment.setAuthorId(userId);
        comment.setEntityType(entityType);
        comment.setEntityId(entityId);
        comment.setProjectId(projectId);
        comment = commentRepository.save(comment);

        audit(projectId, userId, AuditAction.CREATED, comment);

        return toResponse(comment);
    }

    @Transactional
    public CommentResponse update(UUID projectId, UUID commentId, UpdateCommentRequest request, UUID userId) {
        Comment comment = commentRepository.findById(commentId)
                .filter(c -> c.getProjectId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));

        if (!comment.getAuthorId().equals(userId)) {
            throw new IllegalStateException("Only the author can edit a comment");
        }

        comment.setContent(request.content());
        comment = commentRepository.save(comment);

        audit(projectId, userId, AuditAction.UPDATED, comment);

        return toResponse(comment);
    }

    @Transactional
    public void delete(UUID projectId, UUID commentId, UUID userId) {
        Comment comment = commentRepository.findById(commentId)
                .filter(c -> c.getProjectId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("Comment", commentId));

        if (!canDelete(userId, comment)) {
            throw new IllegalStateException("Not authorized to delete this comment");
        }

        audit(projectId, userId, AuditAction.DELETED, comment);

        commentRepository.delete(comment);
    }

    private CommentResponse toResponse(Comment comment) {
        String displayName = userService.findEntityById(comment.getAuthorId())
                .map(User::getDisplayName)
                .orElse(null);
        return new CommentResponse(
                comment.getId(),
                comment.getContent(),
                comment.getAuthorId(),
                displayName,
                comment.getEntityType(),
                comment.getEntityId(),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                comment.getCreatedBy(),
                comment.getUpdatedBy()
        );
    }

    /** Where a comment is, for the audit trail (PRD-046): the case, or for a result its run. */
    private record CommentPlace(AuditParent parent, String name) {
    }

    private void validateEntityBelongsToProject(UUID projectId, CommentEntityType entityType, UUID entityId) {
        placeOf(projectId, entityType, entityId);
    }

    /** Resolves what the comment is on, within the project; anything else is a 404. */
    private CommentPlace placeOf(UUID projectId, CommentEntityType entityType, UUID entityId) {
        return switch (entityType) {
            case TEST_CASE -> {
                var testCase = testCaseRepository.findById(entityId)
                        .filter(tc -> tc.getProject().getId().equals(projectId))
                        .orElseThrow(() -> new ResourceNotFoundException("TestCase", entityId));
                yield new CommentPlace(new AuditParent(AuditEntityType.TEST_CASE, testCase.getId()),
                        testCase.getKey() + " " + testCase.getTitle());
            }
            case TEST_RESULT -> {
                var testResult = testResultRepository.findById(entityId)
                        .filter(tr -> tr.getTestRun().getProject().getId().equals(projectId))
                        .orElseThrow(() -> new ResourceNotFoundException("TestResult", entityId));
                var run = testResult.getTestRun();
                yield new CommentPlace(new AuditParent(AuditEntityType.TEST_RUN, run.getId()),
                        run.getName() + " · " + testResult.getTestCase().getTitle());
            }
        };
    }

    private void audit(UUID projectId, UUID userId, AuditAction action, Comment comment) {
        CommentPlace place = placeOf(projectId, comment.getEntityType(), comment.getEntityId());
        auditService.log(projectId, userId, action, AuditEntityType.COMMENT, comment.getId(), place.name(), null,
                FieldChanges.none(), place.parent());
    }

    private boolean canDelete(UUID userId, Comment comment) {
        if (comment.getAuthorId().equals(userId)) {
            return true;
        }
        boolean isProjectAdmin = projectMemberRepository.findByUserIdAndProjectId(userId, comment.getProjectId())
                .map(member -> member.getRole() == ProjectRole.ADMIN)
                .orElse(false);
        if (isProjectAdmin) {
            return true;
        }
        return userService.findEntityById(userId)
                .map(User::isSystemAdmin)
                .orElse(false);
    }
}
