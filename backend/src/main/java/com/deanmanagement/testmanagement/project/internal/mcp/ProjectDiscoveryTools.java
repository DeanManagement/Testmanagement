package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.entity.McpToolGroup;
import com.deanmanagement.testmanagement.project.internal.dto.testCaseFolder.CreateTestCaseFolderRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCaseFolder.UpdateTestCaseFolderRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCaseFolder.MoveTestCasesRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCaseFolder.ReorderFoldersRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCaseFolder.TestCaseFolderResponse;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestPlanRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestSuiteRepository;
import com.deanmanagement.testmanagement.project.internal.service.CustomFieldService;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseFolderService;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Orientation tools (PRD-025 §3.4). An agent calls these first to learn what it is working in.
 */
@Service
@InToolGroup(McpToolGroup.AUTHORING)
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectDiscoveryTools {

    private final McpCallerContext callerContext;
    private final ProjectRepository projectRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestSuiteRepository testSuiteRepository;
    private final TestPlanRepository testPlanRepository;
    private final TestCaseFolderService folderService;
    private final McpWriteThrottle writeThrottle;
    private final McpValidator validator;
    private final CustomFieldService customFieldService;

    // Identity, not authoring: a key restricted to running tests still has to be able to ask
    // which project it is scoped to and what role it holds.
    @InToolGroup(McpToolGroup.CORE)
    @McpTool(
            name = "get_project",
            description = """
                    Describe the project this API key is scoped to, with counts of test cases,
                    suites and plans, and the role your key holds (VIEWER can only read; TESTER can
                    also create and update). Call this first to confirm which project you are in.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public McpDtos.ProjectInfo getProject() {
        var caller = callerContext.require();
        Project project = projectRepository.findById(caller.projectId())
                .orElseThrow(() -> new McpToolException("The key's project no longer exists."));
        return new McpDtos.ProjectInfo(
                project.getId(),
                project.getKey(),
                project.getName(),
                project.getDescription(),
                testCaseRepository.countByProjectId(project.getId()),
                testSuiteRepository.countByProjectId(project.getId()),
                testPlanRepository.countByProjectId(project.getId()),
                caller.role().name());
    }

    @McpTool(
            name = "list_custom_fields",
            description = """
                    The project's custom fields (PRD-035): extra typed attributes on test cases,
                    test runs and bug reports. Call this before writing customFields anywhere —
                    values are keyed by field NAME, not id, and an unknown name is refused.
                    fieldType: TEXT (max 500 chars) | NUMBER | DATE (yyyy-MM-dd) | SELECT (one of
                    options) | MULTI_SELECT (an array of options). Archived fields keep their values
                    but should not be filled in.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public List<McpDtos.CustomField> listCustomFields(
            @McpToolParam(description = "Only fields of TEST_CASE, TEST_RUN or BUG_REPORT; omit for all",
                    required = false) CustomFieldEntityType entityType) {
        var caller = callerContext.require();
        return customFieldService.list(caller.projectId(), entityType).stream()
                .map(f -> new McpDtos.CustomField(f.name(), f.entityType(), f.fieldType(),
                        f.options().isEmpty() ? null : f.options(), f.required(), f.archived()))
                .toList();
    }

    @McpTool(
            name = "list_test_case_folders",
            description = """
                    The project's folder tree. Use it to pick a folderId when creating a test case:
                    cases without one land at the root, which is usually not where a human expects
                    them. Returns nested folders with their test-case counts.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public List<McpDtos.Folder> listTestCaseFolders() {
        var caller = callerContext.require();
        return folderService.getTree(caller.projectId()).stream()
                .map(ProjectDiscoveryTools::toFolder)
                .toList();
    }

    @McpTool(
            name = "create_test_case_folder",
            description = """
                    Create a folder to organise test cases. Pass parentId to nest it under an
                    existing folder, or omit it for a top-level folder. Call
                    list_test_case_folders first: folder names are not unique, so creating one that
                    already exists just produces two folders with the same name.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = false))
    @Transactional
    public McpDtos.Folder createTestCaseFolder(
            @McpToolParam(description = "Folder name, max 255 characters") String name,
            @McpToolParam(description = "Parent folder id; omit for a top-level folder", required = false)
            UUID parentId) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        var request = new CreateTestCaseFolderRequest(name, parentId);
        validator.validate(request);

        TestCaseFolderResponse created =
                folderService.create(caller.projectId(), request, caller.userId());
        return new McpDtos.Folder(created.id(), created.name(), created.parentId(),
                created.testCaseCount(), List.of());
    }

    @McpTool(
            name = "rename_test_case_folder",
            description = """
                    Rename a folder. Its place in the tree and the test cases in it are unchanged.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true))
    @Transactional
    public McpDtos.Folder renameTestCaseFolder(
            @McpToolParam(description = "Folder UUID from list_test_case_folders") UUID folderId,
            @McpToolParam(description = "New folder name, max 255 characters") String name) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        var request = new UpdateTestCaseFolderRequest(name);
        validator.validate(request);

        TestCaseFolderResponse renamed =
                folderService.update(caller.projectId(), folderId, request, caller.userId());
        return new McpDtos.Folder(renamed.id(), renamed.name(), renamed.parentId(),
                renamed.testCaseCount(), List.of());
    }

    @McpTool(
            name = "move_test_case_folder",
            description = """
                    Move a folder, with everything in it, under another folder — or to the top
                    level if you omit parentId. It is placed after the folders already there.
                    A folder cannot be moved into itself or into one of its own subfolders.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true))
    @Transactional
    public McpDtos.Folder moveTestCaseFolder(
            @McpToolParam(description = "UUID of the folder to move") UUID folderId,
            @McpToolParam(description = "New parent folder id; omit for the top level",
                    required = false) UUID parentId) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        if (folderId == null) {
            throw new McpToolException("folderId is required.");
        }

        List<TestCaseFolderResponse> tree = folderService.getTree(caller.projectId());
        List<TestCaseFolderResponse> siblings = parentId == null ? tree
                : find(tree, parentId)
                        .orElseThrow(() -> new ResourceNotFoundException("TestCaseFolder", parentId))
                        .children();
        int sortOrder = siblings.stream()
                .filter(sibling -> !sibling.id().equals(folderId))
                .mapToInt(TestCaseFolderResponse::sortOrder)
                .max().orElse(-1) + 1;

        var request = new ReorderFoldersRequest(
                List.of(new ReorderFoldersRequest.FolderOrder(folderId, parentId, sortOrder)));
        try {
            tree = folderService.reorder(caller.projectId(), request, caller.userId());
        } catch (IllegalArgumentException circular) {
            throw new McpToolException("A folder cannot be moved into itself or into one of its "
                    + "own subfolders. Call list_test_case_folders to see the tree.");
        }
        return find(tree, folderId).map(ProjectDiscoveryTools::toFolder)
                .orElseThrow(() -> new ResourceNotFoundException("TestCaseFolder", folderId));
    }

    @McpTool(
            name = "move_test_cases_to_folder",
            description = """
                    File existing test cases into a folder. Pass folderId to move them there, or
                    omit it to move them back to the project root. Every id must name a test case in
                    this project; if one does not, nothing is moved.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true))
    @Transactional
    public McpDtos.MoveResult moveTestCasesToFolder(
            @McpToolParam(description = "UUIDs of the test cases to move") List<UUID> testCaseIds,
            @McpToolParam(description = "Target folder id; omit to move to the project root", required = false)
            UUID folderId) {

        var caller = callerContext.requireWriter();
        if (testCaseIds == null || testCaseIds.isEmpty()) {
            throw new McpToolException("testCaseIds is required.");
        }
        writeThrottle.recordWrite(caller.apiKeyId());

        folderService.moveTestCases(caller.projectId(),
                new MoveTestCasesRequest(testCaseIds, folderId), caller.userId());
        return new McpDtos.MoveResult(testCaseIds.size(), folderId);
    }

    private static Optional<TestCaseFolderResponse> find(List<TestCaseFolderResponse> folders,
                                                         UUID id) {
        for (TestCaseFolderResponse folder : folders) {
            if (folder.id().equals(id)) {
                return Optional.of(folder);
            }
            Optional<TestCaseFolderResponse> nested =
                    find(folder.children() == null ? List.of() : folder.children(), id);
            if (nested.isPresent()) {
                return nested;
            }
        }
        return Optional.empty();
    }

    private static McpDtos.Folder toFolder(TestCaseFolderResponse folder) {
        List<McpDtos.Folder> children = folder.children() == null ? List.of()
                : folder.children().stream().map(ProjectDiscoveryTools::toFolder).toList();
        return new McpDtos.Folder(folder.id(), folder.name(), folder.parentId(),
                folder.testCaseCount(), children);
    }
}
