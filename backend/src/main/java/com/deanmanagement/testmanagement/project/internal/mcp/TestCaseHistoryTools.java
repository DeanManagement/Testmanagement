package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.entity.McpToolGroup;
import com.deanmanagement.testmanagement.project.internal.dto.version.TestCaseVersionResponse;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseVersionService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Version history of a test case (PRD-011) — read-only, because every edit already snapshots the
 * state it replaces and there is nothing for a caller to write.
 */
@Service
@InToolGroup(McpToolGroup.AUTHORING)
@RequiredArgsConstructor
public class TestCaseHistoryTools {

    private final McpCallerContext callerContext;
    private final TestCaseVersionService versionService;
    private final TestCaseRepository testCaseRepository;

    @McpTool(
            name = "list_test_case_versions",
            description = """
                    The edit history of a test case, newest first. Every update_test_case (and
                    every human edit) keeps the state it replaced as a numbered version. Use it to
                    see whether a case changed since a run executed it, then get_test_case_version
                    to read what it said at the time.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    @Transactional(readOnly = true)
    public McpDtos.VersionList listTestCaseVersions(
            @McpToolParam(description = "Test case key (PROJ-12) or UUID") String testCaseIdOrKey) {

        var caller = callerContext.require();
        List<McpDtos.VersionSummary> versions =
                versionService.list(caller.projectId(), testCaseId(caller, testCaseIdOrKey)).stream()
                        .map(v -> new McpDtos.VersionSummary(v.versionNumber(), v.versionAt(),
                                v.title(), v.current()))
                        .toList();
        return new McpDtos.VersionList(versions, versions.size());
    }

    @McpTool(
            name = "get_test_case_version",
            description = """
                    A test case exactly as it stood at one version, steps included. Take the
                    versionNumber from list_test_case_versions.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    @Transactional(readOnly = true)
    public McpDtos.VersionDetail getTestCaseVersion(
            @McpToolParam(description = "Test case key (PROJ-12) or UUID") String testCaseIdOrKey,
            @McpToolParam(description = "Version number from list_test_case_versions")
            Integer versionNumber) {

        var caller = callerContext.require();
        if (versionNumber == null) {
            throw new McpToolException("versionNumber is required. Call list_test_case_versions "
                    + "to see which exist.");
        }
        TestCaseVersionResponse version = versionService.get(caller.projectId(),
                testCaseId(caller, testCaseIdOrKey), versionNumber);

        List<McpDtos.Step> steps = version.steps() == null ? List.of() : version.steps().stream()
                .sorted(Comparator.comparingInt(TestCaseVersionResponse.StepSnapshot::orderIndex))
                .map(s -> new McpDtos.Step(s.action(), s.expectedResult(), s.testData()))
                .toList();
        return new McpDtos.VersionDetail(version.versionNumber(), version.versionAt(),
                version.title(), version.description(), version.preconditions(), version.priority(),
                version.status(), version.labels(), steps);
    }

    private UUID testCaseId(McpCallerContext.Caller caller, String testCaseIdOrKey) {
        return McpTestCaseReferences.resolve(testCaseRepository, caller.projectId(),
                testCaseIdOrKey).getId();
    }
}
