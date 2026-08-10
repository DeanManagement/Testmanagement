package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.testCaseFolder.TestCaseFolderResponse;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestPlanRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestSuiteRepository;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseFolderService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Orientation tools (PRD-025 §3.4). An agent calls these first to learn what it is working in.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProjectDiscoveryTools {

    private final McpCallerContext callerContext;
    private final ProjectRepository projectRepository;
    private final TestCaseRepository testCaseRepository;
    private final TestSuiteRepository testSuiteRepository;
    private final TestPlanRepository testPlanRepository;
    private final TestCaseFolderService folderService;

    @McpTool(
            name = "get_project",
            description = """
                    Describe the project this API key is scoped to, with counts of test cases,
                    suites and plans, and the role your key holds (VIEWER can only read; TESTER can
                    also create and update). Call this first to confirm which project you are in.
                    """,
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
            name = "list_test_case_folders",
            description = """
                    The project's folder tree. Use it to pick a folderId when creating a test case:
                    cases without one land at the root, which is usually not where a human expects
                    them. Returns nested folders with their test-case counts.
                    """,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    public List<McpDtos.Folder> listTestCaseFolders() {
        var caller = callerContext.require();
        return folderService.getTree(caller.projectId()).stream()
                .map(ProjectDiscoveryTools::toFolder)
                .toList();
    }

    private static McpDtos.Folder toFolder(TestCaseFolderResponse folder) {
        List<McpDtos.Folder> children = folder.children() == null ? List.of()
                : folder.children().stream().map(ProjectDiscoveryTools::toFolder).toList();
        return new McpDtos.Folder(folder.id(), folder.name(), folder.parentId(),
                folder.testCaseCount(), children);
    }
}
