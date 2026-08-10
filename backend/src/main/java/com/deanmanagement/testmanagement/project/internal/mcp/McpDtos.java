package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestPlanStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
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

    /*
     * A note on @Nullable, which is load-bearing in both directions.
     *
     * On inputs it keeps an optional argument optional. On outputs it is what stops a tool call
     * failing outright: with an output schema declared, Spring AI validates the response against
     * it, and an unannotated String is "required, type string". A project with no description then
     * fails validation and the caller gets an error instead of its answer — which is how this was
     * found. Anything the domain can leave empty has to say so here.
     *
     * And @Nullable alone is not enough: it makes a property optional, and optional in JSON Schema
     * means *absent*, not present-and-null. Every record here is therefore serialised with
     * NON_NULL so an empty field is omitted rather than sent as null — which also gives the caller
     * one less shape to reason about.
     */

    private McpDtos() {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ProjectInfo(UUID id, String key, String name, @Nullable String description,
                       long testCaseCount, long testSuiteCount, long testPlanCount,
                       String yourRole) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TestCaseSummary(UUID id, String key, String title, TestCaseStatus status,
                           Priority priority, @Nullable Set<String> labels,
                           @Nullable UUID folderId) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TestCasePage(List<TestCaseSummary> testCases, int page, int size, long totalElements,
                        boolean hasMore) {}

    /**
     * A test step as an agent supplies it.
     *
     * <p>The {@code @Nullable}s are load-bearing, not documentation. Spring AI generates the tool's
     * JSON schema with victools, which marks every property of a nested type <em>required</em>
     * unless it is annotated nullable — {@code @McpToolParam(required = false)} only applies to
     * top-level method parameters. Without these, a client is rejected at schema validation, before
     * any of this code runs, for omitting {@code testData} on a step that has none. Most steps have
     * none.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Step(String action,
                @Nullable String expectedResult,
                @Nullable String testData) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TestCaseDetail(UUID id, String key, String title, @Nullable String description,
                          @Nullable String preconditions, TestCaseStatus status, Priority priority,
                          @Nullable Set<String> labels, @Nullable UUID folderId,
                          List<Step> steps) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Folder(UUID id, String name, @Nullable UUID parentId, long testCaseCount,
                  List<Folder> children) {}

    /** @param folderId null when the cases were moved back to the project root */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record MoveResult(int moved, @Nullable UUID folderId) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SuiteSummary(UUID id, String name, @Nullable String description, int testCaseCount) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SuitePage(List<SuiteSummary> testSuites, int page, int size, long totalElements,
                     boolean hasMore) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SuiteDetail(UUID id, String name, @Nullable String description,
                       List<TestCaseRef> testCases) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TestCaseRef(UUID id, String title) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PlanSummary(UUID id, String name, @Nullable String description, TestPlanStatus status,
                       @Nullable LocalDate targetDate, int testRunCount) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PlanDetail(UUID id, String name, TestPlanStatus status, @Nullable LocalDate targetDate,
                      int totalRuns, int completedRuns, int passed, int failed, int blocked,
                      int skipped, int pending, double passRate) {}

    /** What a create returned, plus the key an agent should quote back to a human. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CreatedTestCase(UUID id, String key, String title, TestCaseStatus status) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CreatedSuite(UUID id, String name, int testCaseCount) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CreatedPlan(UUID id, String name, TestPlanStatus status) {}

    /**
     * One item's fate in a bulk create. Partial success is the normal outcome, not an error: an
     * agent recovering from item 37 of 50 needs to know which 36 landed.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record BulkItemResult(int index, String outcome, @Nullable UUID id, @Nullable String key,
                          @Nullable String message) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record BulkResult(boolean dryRun, int created, int skipped, int failed,
                      List<BulkItemResult> results) {}

    /** A near-match that blocked a create, so the agent can update it instead. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DuplicateCandidate(UUID id, String key, String title) {}

    // --- executions (read-only) --------------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TestRunSummary(UUID id, String key, String name, @Nullable String environment,
                          TestRunStatus status, int total, int passed, int failed, int blocked,
                          int skipped, int pending, @Nullable Instant endTime) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TestRunPage(List<TestRunSummary> testRuns, int page, int size, long totalElements,
                       boolean hasMore) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TestResult(UUID testCaseId, String testCaseTitle, TestResultStatus status,
                      @Nullable String comment, @Nullable String defectLink) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TestRunDetail(UUID id, String key, String name, @Nullable String environment,
                         TestRunStatus status, @Nullable Instant startTime, @Nullable Instant endTime,
                         List<TestResult> results) {}

    // --- requirements and traceability -------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Requirement(UUID id, String externalId, String title, @Nullable String description,
                       List<TestCaseRef> testCases) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RequirementPage(List<Requirement> requirements, int page, int size, long totalElements,
                           boolean hasMore) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TraceabilityCell(UUID testCaseId, String testCaseKey, String testCaseTitle, String status) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TraceabilityRow(UUID requirementId, String externalId, String title, String coverage,
                           List<TraceabilityCell> cells) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CoverageSummary(long totalRequirements, long uncovered, long untested, long failing,
                           long passing, double coveragePercent) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TraceabilityMatrix(List<TraceabilityRow> requirements, CoverageSummary summary) {}
}
