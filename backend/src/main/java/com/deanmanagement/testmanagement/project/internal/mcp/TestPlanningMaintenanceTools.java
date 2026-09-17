package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.entity.McpToolGroup;
import com.deanmanagement.testmanagement.project.internal.dto.TestSuiteResponse;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestSuiteRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testSuite.BulkTestCasesRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.TestPlanResponse;
import com.deanmanagement.testmanagement.project.internal.dto.testplan.UpdateTestPlanRequest;
import com.deanmanagement.testmanagement.project.internal.entity.TestPlanStatus;
import com.deanmanagement.testmanagement.project.internal.service.TestPlanService;
import com.deanmanagement.testmanagement.project.internal.service.TestSuiteService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Changing a suite or plan that already exists — {@link TestPlanningTools} can only create them.
 *
 * <p>The update tools are partial ("only what you pass changes") over services that are full
 * replace, because the SPA sends the whole object and relies on null to clear a field. So the merge
 * happens here. PRD-025 §8 removed a merge like this from {@code update_test_case} for making the
 * agent the author of fields it never touched; that does not apply while read and write share one
 * transaction, which is why these methods must stay {@code @Transactional}: the service then
 * updates the very entity instance the read loaded, and a field set back to its own value is not
 * dirty.
 */
@Service
@InToolGroup(McpToolGroup.AUTHORING)
@RequiredArgsConstructor
public class TestPlanningMaintenanceTools {

    private final McpCallerContext callerContext;
    private final TestSuiteService testSuiteService;
    private final TestPlanService testPlanService;
    private final McpWriteThrottle writeThrottle;
    private final McpValidator validator;

    // --- suites ----------------------------------------------------------------------------

    @McpTool(
            name = "update_test_suite",
            description = """
                    Rename a test suite or change its description. Only the fields you pass are
                    changed; to CLEAR the description pass an empty string "".
                    To change which cases the suite holds, use add_test_cases_to_suite and
                    remove_test_cases_from_suite.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true))
    @Transactional
    public McpDtos.SuiteSummary updateTestSuite(
            @McpToolParam(description = "Test suite UUID") UUID id,
            @McpToolParam(description = "New name, max 255 characters", required = false) String name,
            @McpToolParam(description = "New description; \"\" clears it", required = false)
            String description) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        if (name == null && description == null) {
            throw new McpToolException("Nothing to update: pass name or description.");
        }

        TestSuiteResponse current = testSuiteService.findById(caller.projectId(), id);
        // testCaseIds null: the service leaves membership alone.
        var request = new UpdateTestSuiteRequest(
                name == null ? current.name() : name,
                description == null ? current.description() : emptyToNull(description),
                null);
        validator.validate(request);

        TestSuiteResponse suite =
                testSuiteService.update(caller.projectId(), id, request, caller.userId());
        return new McpDtos.SuiteSummary(suite.id(), suite.name(), suite.description(),
                sizeOf(suite));
    }

    @McpTool(
            name = "add_test_cases_to_suite",
            description = """
                    Add test cases to an existing suite, up to 100 per call. Every id must name a
                    test case in this project; if one does not, nothing is added. Adding a case the
                    suite already holds is harmless — `changed` tells you how many were new.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true))
    @Transactional
    public McpDtos.SuiteMembership addTestCasesToSuite(
            @McpToolParam(description = "Test suite UUID") UUID suiteId,
            @McpToolParam(description = "UUIDs of the test cases to add") Set<UUID> testCaseIds) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        validator.validate(new BulkTestCasesRequest(testCaseIds));

        int before = sizeOf(testSuiteService.findById(caller.projectId(), suiteId));
        testSuiteService.bulkAddTestCases(caller.projectId(), suiteId, testCaseIds, caller.userId());
        return membership(caller, suiteId, before);
    }

    @McpTool(
            name = "remove_test_cases_from_suite",
            description = """
                    Take test cases out of a suite, up to 100 per call. The test cases themselves
                    are untouched, and so are runs already seeded from the suite. An id the suite
                    does not hold is ignored — `changed` tells you how many were actually removed.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true))
    @Transactional
    public McpDtos.SuiteMembership removeTestCasesFromSuite(
            @McpToolParam(description = "Test suite UUID") UUID suiteId,
            @McpToolParam(description = "UUIDs of the test cases to remove") Set<UUID> testCaseIds) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        validator.validate(new BulkTestCasesRequest(testCaseIds));

        int before = sizeOf(testSuiteService.findById(caller.projectId(), suiteId));
        testSuiteService.bulkRemoveTestCases(caller.projectId(), suiteId, testCaseIds,
                caller.userId());
        return membership(caller, suiteId, before);
    }

    // --- plans -----------------------------------------------------------------------------

    @McpTool(
            name = "update_test_plan",
            description = """
                    Rename a test plan, change its description or target date, or move its status.
                    Only the fields you pass are changed; to CLEAR the description pass an empty
                    string "". A target date can be changed here but not cleared.
                    status: OPEN | IN_PROGRESS | COMPLETED | CANCELLED.
                    The assignee is left as it is: assigning work is a human's decision.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(destructiveHint = false, idempotentHint = true))
    @Transactional
    public McpDtos.PlanSummary updateTestPlan(
            @McpToolParam(description = "Test plan UUID") UUID id,
            @McpToolParam(description = "New name, max 255 characters", required = false) String name,
            @McpToolParam(description = "New description; \"\" clears it", required = false)
            String description,
            @McpToolParam(description = "OPEN, IN_PROGRESS, COMPLETED or CANCELLED", required = false)
            TestPlanStatus status,
            @McpToolParam(description = "Target date as YYYY-MM-DD", required = false)
            LocalDate targetDate) {

        var caller = callerContext.requireWriter();
        writeThrottle.recordWrite(caller.apiKeyId());
        if (name == null && description == null && status == null && targetDate == null) {
            throw new McpToolException(
                    "Nothing to update: pass name, description, status or targetDate.");
        }

        TestPlanResponse current = testPlanService.findById(caller.projectId(), id);
        var request = new UpdateTestPlanRequest(
                name == null ? current.name() : name,
                description == null ? current.description() : emptyToNull(description),
                status,
                targetDate == null ? current.targetDate() : targetDate,
                // Echoed, not chosen: the service reads a null here as "clear the assignee".
                current.assigneeId());
        validator.validate(request);

        TestPlanResponse plan =
                testPlanService.update(caller.projectId(), id, request, caller.userId());
        return new McpDtos.PlanSummary(plan.id(), plan.name(), plan.description(), plan.status(),
                plan.targetDate(), plan.testRunCount());
    }

    private McpDtos.SuiteMembership membership(McpCallerContext.Caller caller, UUID suiteId,
                                               int sizeBefore) {
        TestSuiteResponse suite = testSuiteService.findById(caller.projectId(), suiteId);
        int size = sizeOf(suite);
        return new McpDtos.SuiteMembership(suite.id(), suite.name(), size,
                Math.abs(size - sizeBefore));
    }

    private static int sizeOf(TestSuiteResponse suite) {
        return suite.testCases() == null ? 0 : suite.testCases().size();
    }

    private static String emptyToNull(String value) {
        return value.isBlank() ? null : value;
    }
}
