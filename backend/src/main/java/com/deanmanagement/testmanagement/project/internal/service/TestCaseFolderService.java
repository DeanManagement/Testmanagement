package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.testCaseFolder.CreateTestCaseFolderRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCaseFolder.MoveTestCasesRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCaseFolder.ReorderFoldersRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCaseFolder.TestCaseFolderMapper;
import com.deanmanagement.testmanagement.project.internal.dto.testCaseFolder.TestCaseFolderResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testCaseFolder.UpdateTestCaseFolderRequest;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseFolder;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseFolderRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TestCaseFolderService {

    private final TestCaseFolderRepository folderRepository;
    private final TestCaseRepository testCaseRepository;
    private final ProjectRepository projectRepository;
    private final TestCaseFolderMapper folderMapper;
    private final AuditService auditService;

    public List<TestCaseFolderResponse> getTree(UUID projectId) {
        List<TestCaseFolder> allFolders = folderRepository.findByProjectIdOrderBySortOrderAsc(projectId);

        Map<UUID, List<TestCaseFolder>> childrenMap = new HashMap<>();
        List<TestCaseFolder> roots = new ArrayList<>();

        for (TestCaseFolder folder : allFolders) {
            if (folder.getParent() == null) {
                roots.add(folder);
            } else {
                childrenMap.computeIfAbsent(folder.getParent().getId(), k -> new ArrayList<>()).add(folder);
            }
        }

        return roots.stream()
                .map(root -> buildTreeNode(root, childrenMap))
                .toList();
    }

    @Transactional
    public TestCaseFolderResponse create(UUID projectId, CreateTestCaseFolderRequest request, UUID userId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));

        TestCaseFolder folder = folderMapper.toEntity(request);
        folder.setProject(project);

        if (request.parentId() != null) {
            TestCaseFolder parent = folderRepository.findById(request.parentId())
                    .filter(f -> f.getProject().getId().equals(projectId))
                    .orElseThrow(() -> new ResourceNotFoundException("TestCaseFolder", request.parentId()));
            folder.setParent(parent);
        }

        long count = folderRepository.countByProjectId(projectId);
        folder.setSortOrder((int) count);

        folder = folderRepository.save(folder);
        auditService.log(projectId, userId, AuditAction.CREATED,
                AuditEntityType.TEST_CASE_FOLDER, folder.getId(), folder.getName(), null);

        return folderMapper.toResponse(folder);
    }

    @Transactional
    public TestCaseFolderResponse update(UUID projectId, UUID folderId, UpdateTestCaseFolderRequest request, UUID userId) {
        TestCaseFolder folder = folderRepository.findById(folderId)
                .filter(f -> f.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestCaseFolder", folderId));

        folder.setName(request.name());
        folder = folderRepository.save(folder);

        auditService.log(projectId, userId, AuditAction.UPDATED,
                AuditEntityType.TEST_CASE_FOLDER, folder.getId(), folder.getName(), null);

        return folderMapper.toResponse(folder);
    }

    @Transactional
    public void delete(UUID projectId, UUID folderId, UUID userId) {
        TestCaseFolder folder = folderRepository.findById(folderId)
                .filter(f -> f.getProject().getId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("TestCaseFolder", folderId));

        for (TestCaseFolder child : folder.getChildren()) {
            child.setParent(folder.getParent());
            folderRepository.save(child);
        }

        for (TestCase tc : folder.getTestCases()) {
            tc.setFolder(null);
            testCaseRepository.save(tc);
        }

        auditService.log(projectId, userId, AuditAction.DELETED,
                AuditEntityType.TEST_CASE_FOLDER, folder.getId(), folder.getName(), null);

        folderRepository.delete(folder);
    }

    @Transactional
    public List<TestCaseFolderResponse> reorder(UUID projectId, ReorderFoldersRequest request, UUID userId) {
        Map<UUID, ReorderFoldersRequest.FolderOrder> orderMap = new HashMap<>();
        for (ReorderFoldersRequest.FolderOrder fo : request.folders()) {
            orderMap.put(fo.id(), fo);
        }

        validateNoCircularRefs(orderMap);

        List<TestCaseFolder> allFolders = folderRepository.findByProjectIdOrderBySortOrderAsc(projectId);
        Map<UUID, TestCaseFolder> folderMap = new HashMap<>();
        for (TestCaseFolder f : allFolders) {
            folderMap.put(f.getId(), f);
        }

        for (ReorderFoldersRequest.FolderOrder fo : request.folders()) {
            TestCaseFolder folder = folderMap.get(fo.id());
            if (folder == null || !folder.getProject().getId().equals(projectId)) {
                throw new ResourceNotFoundException("TestCaseFolder", fo.id());
            }

            if (fo.parentId() != null) {
                TestCaseFolder parent = folderMap.get(fo.parentId());
                if (parent == null) {
                    throw new ResourceNotFoundException("TestCaseFolder", fo.parentId());
                }
                folder.setParent(parent);
            } else {
                folder.setParent(null);
            }
            folder.setSortOrder(fo.sortOrder());
        }

        folderRepository.saveAll(allFolders);

        auditService.log(projectId, userId, AuditAction.UPDATED,
                AuditEntityType.TEST_CASE_FOLDER, null, null, "Reordered folders");

        return getTree(projectId);
    }

    @Transactional
    public void moveTestCases(UUID projectId, MoveTestCasesRequest request, UUID userId) {
        final TestCaseFolder targetFolder = request.targetFolderId() == null ? null
                : folderRepository.findById(request.targetFolderId())
                        .filter(f -> f.getProject().getId().equals(projectId))
                        .orElseThrow(() ->
                                new ResourceNotFoundException("TestCaseFolder", request.targetFolderId()));

        // Scoped lookup, and every requested id must resolve. Previously this loaded by id alone
        // and then rejected a foreign case by message, which both leaked that the id exists and
        // silently ignored ids that do not — so a typo moved fewer cases than asked, quietly.
        List<TestCase> testCases =
                testCaseRepository.findByIdInAndProjectId(request.testCaseIds(), projectId);
        if (testCases.size() != new HashSet<>(request.testCaseIds()).size()) {
            Set<UUID> missing = new HashSet<>(request.testCaseIds());
            testCases.forEach(tc -> missing.remove(tc.getId()));
            throw new ResourceNotFoundException("TestCase", missing.iterator().next());
        }
        testCases.forEach(tc -> tc.setFolder(targetFolder));
        testCaseRepository.saveAll(testCases);

        String folderName = targetFolder != null ? targetFolder.getName() : "root";
        auditService.log(projectId, userId, AuditAction.MOVED,
                AuditEntityType.TEST_CASE, null, null,
                "Moved " + testCases.size() + " test cases to folder: " + folderName);
    }

    private TestCaseFolderResponse buildTreeNode(TestCaseFolder folder, Map<UUID, List<TestCaseFolder>> childrenMap) {
        TestCaseFolderResponse response = folderMapper.toResponse(folder);
        List<TestCaseFolder> children = childrenMap.getOrDefault(folder.getId(), List.of());
        List<TestCaseFolderResponse> childResponses = children.stream()
                .map(child -> buildTreeNode(child, childrenMap))
                .toList();

        return new TestCaseFolderResponse(
                response.id(),
                response.name(),
                response.parentId(),
                response.sortOrder(),
                response.testCaseCount(),
                childResponses,
                response.createdAt(),
                response.updatedAt()
        );
    }

    private void validateNoCircularRefs(Map<UUID, ReorderFoldersRequest.FolderOrder> orderMap) {
        for (ReorderFoldersRequest.FolderOrder fo : orderMap.values()) {
            Set<UUID> visited = new HashSet<>();
            UUID current = fo.id();
            while (current != null) {
                if (!visited.add(current)) {
                    throw new IllegalArgumentException("Circular folder reference detected for folder: " + fo.id());
                }
                ReorderFoldersRequest.FolderOrder parent = orderMap.get(current);
                current = (parent != null) ? parent.parentId() : null;
            }
        }
    }
}
