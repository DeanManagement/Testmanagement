package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.requirement.CoverageSummaryResponse;
import com.deanmanagement.testmanagement.project.internal.dto.requirement.RequirementResponse;
import com.deanmanagement.testmanagement.project.internal.dto.requirement.SaveRequirementRequest;
import com.deanmanagement.testmanagement.project.internal.dto.requirement.TraceabilityRowResponse;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.service.RequirementService;
import com.deanmanagement.testmanagement.shared.PageableUtils;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Requirements and traceability (PRD-014, exposed for agents by PRD-025).
 *
 * <p>The workflow this exists for: hand an agent a specification, have it record the requirements,
 * link the test cases that cover each one, and then report which requirements nothing proves. That
 * last part is the payoff — mapping cases to requirements is exactly the tedious bookkeeping that
 * gets skipped, and the gap it hides ("we have tests" versus "the tests pass") is the thing an
 * audit asks about.
 */
@Service
@RequiredArgsConstructor
public class RequirementTools {

    private final McpCallerContext callerContext;
    private final RequirementService requirementService;
    private final TestCaseRepository testCaseRepository;
    private final McpValidator validator;
    private final McpWriteThrottle writeThrottle;

    @McpTool(
            name = "list_requirements",
            description = """
                    The project's requirements with the test cases linked to each. Call this before
                    creating one: externalId is how a requirement is recognised, and creating a
                    second requirement with an id that already exists is the usual way this gets
                    messy.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    @Transactional(readOnly = true)
    public McpDtos.RequirementPage listRequirements(
            @McpToolParam(description = "Zero-based page number, default 0", required = false) Integer page,
            @McpToolParam(description = "Page size, default 50, max 200", required = false) Integer size) {

        var caller = callerContext.require();
        var result = requirementService.list(caller.projectId(), PageableUtils.normalize(
                PageRequest.of(page == null || page < 0 ? 0 : page,
                        size == null || size < 1 ? PageableUtils.DEFAULT_SIZE : size)));

        List<McpDtos.Requirement> requirements = result.getContent().stream()
                .map(RequirementTools::toRequirement)
                .toList();
        return new McpDtos.RequirementPage(requirements, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.hasNext());
    }

    @McpTool(
            name = "create_requirement",
            description = """
                    Record a requirement. externalId is the identifier it is known by outside this
                    tool — a Jira key, a spec section number, a clause reference — and must be
                    unique in the project.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = false))
    @Transactional
    public McpDtos.Requirement createRequirement(
            @McpToolParam(description = "External identifier, e.g. REQ-014 or a Jira key. Max 100 characters")
            String externalId,
            @McpToolParam(description = "What the requirement states. Max 500 characters") String title,
            @McpToolParam(description = "Fuller wording, if the title is not the whole story", required = false)
            String description) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        var request = new SaveRequirementRequest(externalId, title, description);
        validator.validate(request);

        return toRequirement(
                requirementService.create(caller.projectId(), request, caller.userId()));
    }

    @McpTool(
            name = "link_test_cases_to_requirement",
            description = """
                    Record that these test cases cover a requirement. Every id must name a test case
                    in this project. Linking the same case twice is harmless — the link simply
                    already exists.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true))
    @Transactional
    public McpDtos.Requirement linkTestCasesToRequirement(
            @McpToolParam(description = "Requirement UUID") UUID requirementId,
            @McpToolParam(description = "UUIDs of the covering test cases") List<UUID> testCaseIds) {

        var caller = callerContext.requireWriter();
        if (testCaseIds == null || testCaseIds.isEmpty()) {
            throw new McpToolException("testCaseIds is required.");
        }
        writeThrottle.recordWrite(caller.apiKeyId());

        // Checked up front so a bad id cannot leave the requirement half-linked, and so a case from
        // another project reports as not-found rather than being quietly skipped.
        List<TestCase> found = testCaseRepository.findByIdInAndProjectId(testCaseIds, caller.projectId());
        if (found.size() != List.copyOf(new java.util.LinkedHashSet<>(testCaseIds)).size()) {
            List<UUID> missing = new ArrayList<>(testCaseIds);
            found.forEach(tc -> missing.remove(tc.getId()));
            throw new ResourceNotFoundException("TestCase", missing.getFirst());
        }

        RequirementResponse updated = null;
        for (TestCase testCase : found) {
            updated = requirementService.linkTestCase(caller.projectId(), requirementId,
                    testCase.getId(), caller.userId());
        }
        return toRequirement(updated);
    }

    @McpTool(
            name = "get_traceability_matrix",
            description = """
                    Which requirements are actually proven, and which only look covered. For each
                    requirement you get its linked cases with the status of each case's most recent
                    result, plus a summary.
                    Status per requirement: PASSED, FAILED, BLOCKED, SKIPPED, UNTESTED (a case is
                    linked but has never run) or UNCOVERED (no case is linked at all). UNTESTED is
                    the one worth acting on — the requirement looks covered on paper and nothing has
                    demonstrated it.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(readOnlyHint = true, destructiveHint = false))
    @Transactional(readOnly = true)
    public McpDtos.TraceabilityMatrix getTraceabilityMatrix() {
        var caller = callerContext.require();
        List<TraceabilityRowResponse> rows = requirementService.matrix(caller.projectId());
        CoverageSummaryResponse coverage = requirementService.coverage(caller.projectId());

        List<McpDtos.TraceabilityRow> mapped = rows.stream()
                .map(row -> new McpDtos.TraceabilityRow(
                        row.requirementId(), row.externalId(), row.title(),
                        row.coverage().name(),
                        row.cells().stream()
                                .map(cell -> new McpDtos.TraceabilityCell(cell.testCaseId(),
                                        cell.testCaseKey(), cell.testCaseTitle(), cell.status().name()))
                                .toList()))
                .toList();

        return new McpDtos.TraceabilityMatrix(mapped, new McpDtos.CoverageSummary(
                coverage.totalRequirements(), coverage.uncovered(), coverage.untested(),
                coverage.failing(), coverage.passing(), coverage.coveragePercent()));
    }

    private static McpDtos.Requirement toRequirement(RequirementResponse requirement) {
        return new McpDtos.Requirement(requirement.id(), requirement.externalId(),
                requirement.title(), requirement.description(),
                requirement.testCases() == null ? List.of() : requirement.testCases().stream()
                        .map(tc -> new McpDtos.TestCaseRef(tc.id(), tc.title()))
                        .toList());
    }
}
