package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.testCasePermission.GrantTestCasePermissionRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCasePermission.TestCasePermissionResponse;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCasePermission;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCasePermissionRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.user.User;
import com.deanmanagement.testmanagement.user.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Manages per-user overrides on individual test cases. Every entry point re-resolves the test case
 * through its project, so a permission can never be read or written across a project boundary even
 * if the caller holds a role on some other project.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TestCasePermissionService {

    private final TestCasePermissionRepository permissionRepository;
    private final TestCaseRepository testCaseRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserService userService;
    private final AuditService auditService;

    public List<TestCasePermissionResponse> listForTestCase(UUID projectId, UUID testCaseId) {
        requireTestCaseInProject(projectId, testCaseId);
        return permissionRepository.findByTestCaseIdOrderByCreatedAtAsc(testCaseId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Grants or updates the override for one user. Re-granting an existing pair updates the flag
     * rather than inserting a duplicate, which the unique constraint would reject.
     */
    @Transactional
    public TestCasePermissionResponse grant(UUID projectId, UUID testCaseId,
                                            GrantTestCasePermissionRequest request, UUID actorId) {
        TestCase testCase = requireTestCaseInProject(projectId, testCaseId);
        requireProjectMember(projectId, request.userId());

        TestCasePermission permission = permissionRepository
                .findByTestCaseIdAndUserId(testCaseId, request.userId())
                .orElseGet(() -> {
                    TestCasePermission created = new TestCasePermission();
                    created.setTestCaseId(testCaseId);
                    created.setUserId(request.userId());
                    return created;
                });
        boolean isNew = permission.getId() == null;
        permission.setCanEdit(request.canEdit());
        permission = permissionRepository.save(permission);

        auditService.log(projectId, actorId, isNew ? AuditAction.CREATED : AuditAction.UPDATED,
                AuditEntityType.TEST_CASE, testCase.getId(), testCase.getTitle(),
                (request.canEdit() ? "Granted edit" : "Granted view-only") + " to user " + request.userId());

        return toResponse(permission);
    }

    @Transactional
    public void revoke(UUID projectId, UUID testCaseId, UUID permissionId, UUID actorId) {
        TestCase testCase = requireTestCaseInProject(projectId, testCaseId);

        TestCasePermission permission = permissionRepository.findById(permissionId)
                .filter(p -> p.getTestCaseId().equals(testCaseId))
                .orElseThrow(() -> new ResourceNotFoundException("TestCasePermission", permissionId));

        auditService.log(projectId, actorId, AuditAction.DELETED,
                AuditEntityType.TEST_CASE, testCase.getId(), testCase.getTitle(),
                "Revoked test case permission for user " + permission.getUserId());

        permissionRepository.delete(permission);
    }

    /** Whether the user holds an explicit edit override on the test case. */
    public boolean canEdit(UUID testCaseId, UUID userId) {
        return permissionRepository.findByTestCaseIdAndUserId(testCaseId, userId)
                .map(TestCasePermission::isCanEdit)
                .orElse(false);
    }

    private TestCase requireTestCaseInProject(UUID projectId, UUID testCaseId) {
        TestCase testCase = testCaseRepository.findById(testCaseId)
                .orElseThrow(() -> new ResourceNotFoundException("TestCase", testCaseId));
        if (!testCase.getProject().getId().equals(projectId)) {
            throw new ResourceNotFoundException("TestCase", testCaseId);
        }
        return testCase;
    }

    private void requireProjectMember(UUID projectId, UUID userId) {
        boolean isMember = projectMemberRepository.findByUserIdAndProjectId(userId, projectId).isPresent();
        if (!isMember) {
            throw new ResourceNotFoundException("ProjectMember", userId);
        }
    }

    private TestCasePermissionResponse toResponse(TestCasePermission permission) {
        String displayName = userService.findEntityById(permission.getUserId())
                .map(User::getDisplayName)
                .orElse(null);
        return new TestCasePermissionResponse(
                permission.getId(),
                permission.getTestCaseId(),
                permission.getUserId(),
                displayName,
                permission.isCanEdit(),
                permission.getCreatedAt(),
                permission.getUpdatedAt()
        );
    }
}
