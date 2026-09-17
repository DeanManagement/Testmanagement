package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.testCaseFolder.CreateTestCaseFolderRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCaseFolder.ReorderFoldersRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCaseFolder.ReorderFoldersRequest.FolderOrder;
import com.deanmanagement.testmanagement.project.internal.dto.testCaseFolder.TestCaseFolderResponse;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("dev")
@Transactional
class TestCaseFolderReorderTest {

    @Autowired
    private TestCaseFolderService folderService;
    @Autowired
    private ProjectRepository projectRepository;

    private UUID projectId;
    private UUID parent;
    private UUID child;

    @BeforeEach
    void setUp() {
        Project project = new Project();
        project.setName("Folders");
        project.setKey("F" + Integer.toHexString(new java.util.Random().nextInt(0xFFFFF)));
        projectId = projectRepository.save(project).getId();
        parent = folderService.create(projectId, new CreateTestCaseFolderRequest("Parent", null), null).id();
        child = folderService.create(projectId, new CreateTestCaseFolderRequest("Child", parent), null).id();
    }

    /**
     * The request names only the folder being moved, so the cycle is invisible unless the stored
     * tree is consulted: Parent -> Child is in the database, not in the request.
     */
    @Test
    void movingAFolderUnderItsOwnDescendantIsRefused() {
        var request = new ReorderFoldersRequest(List.of(new FolderOrder(parent, child, 0)));

        assertThatThrownBy(() -> folderService.reorder(projectId, request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Circular");
    }

    @Test
    void aFolderCannotBeItsOwnParent() {
        var request = new ReorderFoldersRequest(List.of(new FolderOrder(parent, parent, 0)));

        assertThatThrownBy(() -> folderService.reorder(projectId, request, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Circular");
    }

    /** Swapping parent and child in one request is legal: the request breaks the old edge. */
    @Test
    void swappingParentAndChildInOneRequestIsAllowed() {
        var request = new ReorderFoldersRequest(List.of(
                new FolderOrder(child, null, 0),
                new FolderOrder(parent, child, 0)));

        List<TestCaseFolderResponse> tree = folderService.reorder(projectId, request, null);

        assertThat(tree).singleElement().satisfies(root -> {
            assertThat(root.id()).isEqualTo(child);
            assertThat(root.children()).singleElement()
                    .satisfies(nested -> assertThat(nested.id()).isEqualTo(parent));
        });
    }

    @Test
    void movingAFolderToTheRootIsAllowed() {
        var request = new ReorderFoldersRequest(List.of(new FolderOrder(child, null, 1)));

        List<TestCaseFolderResponse> tree = folderService.reorder(projectId, request, null);

        assertThat(tree).extracting(TestCaseFolderResponse::id).containsExactlyInAnyOrder(parent, child);
    }
}
