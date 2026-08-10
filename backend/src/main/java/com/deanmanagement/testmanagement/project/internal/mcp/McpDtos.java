package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestPlanStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Shapes returned by the MCP tools.
 *
 * <p>Deliberately not the REST DTOs. Those carry audit columns, ids the agent has no use for, and
 * nested collections that blow up an agent's context on a list call — a page of 50 full
 * {@code TestCaseResponse}es is mostly noise. These are trimmed to what a model needs to decide
 * what to do next, and every list shape carries {@code totalElements} so an agent can tell it is
 * looking at a slice rather than the whole project.
 */
final class McpDtos {

    private McpDtos() {
    }

    record ProjectInfo(UUID id, String key, String name, String description,
                       long testCaseCount, long testSuiteCount, long testPlanCount,
                       String yourRole) {}

    record TestCaseSummary(UUID id, String key, String title, TestCaseStatus status,
                           Priority priority, Set<String> labels, UUID folderId) {}

    record TestCasePage(List<TestCaseSummary> testCases, int page, int size, long totalElements,
                        boolean hasMore) {}

    record Step(String action, String expectedResult, String testData) {}

    record TestCaseDetail(UUID id, String key, String title, String description,
                          String preconditions, TestCaseStatus status, Priority priority,
                          Set<String> labels, UUID folderId, List<Step> steps) {}

    record Folder(UUID id, String name, UUID parentId, long testCaseCount, List<Folder> children) {}

    record SuiteSummary(UUID id, String name, String description, int testCaseCount) {}

    record SuitePage(List<SuiteSummary> testSuites, int page, int size, long totalElements,
                     boolean hasMore) {}

    record SuiteDetail(UUID id, String name, String description,
                       List<TestCaseRef> testCases) {}

    record TestCaseRef(UUID id, String title) {}

    record PlanSummary(UUID id, String name, String description, TestPlanStatus status,
                       LocalDate targetDate, int testRunCount) {}

    record PlanDetail(UUID id, String name, TestPlanStatus status, LocalDate targetDate,
                      int totalRuns, int completedRuns, int passed, int failed, int blocked,
                      int skipped, int pending, double passRate) {}

    /** What a create returned, plus the key an agent should quote back to a human. */
    record CreatedTestCase(UUID id, String key, String title, TestCaseStatus status) {}

    record CreatedSuite(UUID id, String name, int testCaseCount) {}

    record CreatedPlan(UUID id, String name, TestPlanStatus status) {}

    /**
     * One item's fate in a bulk create. Partial success is the normal outcome, not an error: an
     * agent recovering from item 37 of 50 needs to know which 36 landed.
     */
    record BulkItemResult(int index, String outcome, UUID id, String key, String message) {}

    record BulkResult(boolean dryRun, int created, int skipped, int failed,
                      List<BulkItemResult> results) {}

    /** A near-match that blocked a create, so the agent can update it instead. */
    record DuplicateCandidate(UUID id, String key, String title) {}
}
