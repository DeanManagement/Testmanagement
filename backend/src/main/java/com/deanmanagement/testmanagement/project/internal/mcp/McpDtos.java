package com.deanmanagement.testmanagement.project.internal.mcp;

import com.deanmanagement.testmanagement.project.internal.dto.effort.EffortSummary;
import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import com.deanmanagement.testmanagement.project.internal.entity.BugResolution;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldType;
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
import java.util.Map;
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
    record Step(@Nullable String action,
                @Nullable String expectedResult,
                @Nullable String testData,
                /* PRD-030: on input, a shared step to use in place of action; on output, the shared
                   step a step came from. Consecutive steps with the same id are one reference. */
                @Nullable UUID sharedStepId,
                @Nullable String sharedStepTitle) {
        Step(String action, String expectedResult, String testData) {
            this(action, expectedResult, testData, null, null);
        }
    }

    /*
     * The MCP spec requires structuredContent to be an object, so a list result is wrapped in a
     * record rather than returned bare; strict clients reject a top-level array.
     */
    record FolderList(List<Folder> folders) {}

    record PlanList(List<PlanSummary> testPlans) {}

    record CustomFieldList(List<CustomField> customFields) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SharedStepItem(UUID id, String title, @Nullable String description, int stepCount, long usedByCount) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SharedStepList(List<SharedStepItem> sharedSteps, long totalElements, boolean hasMore) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TestCaseDetail(UUID id, String key, String title, @Nullable String description,
                          @Nullable String preconditions, TestCaseStatus status, Priority priority,
                          @Nullable Set<String> labels, @Nullable UUID folderId,
                          List<Step> steps, @Nullable Map<String, Object> customFields,
                          @Nullable Integer estimateMinutes, @Nullable Long medianActualMs,
                          List<Attachment> attachments) {}

    /** Metadata only (PRD-044 §3.8): the agent can tell a human the file exists, not fetch it. */
    record Attachment(String fileName, String contentType, long sizeBytes) {}

    /** A custom field definition (PRD-035); agents write values keyed by {@code name}. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CustomField(String name, CustomFieldEntityType entityType, CustomFieldType fieldType,
                       @Nullable List<String> options, boolean required, boolean archived) {}

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
                      int skipped, int pending, double passRate, @Nullable EffortSummary effort) {}

    /** @param changed how many cases were actually added or removed; ids already in that state count 0 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SuiteMembership(UUID id, String name, int testCaseCount, int changed) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record BulkStatusResult(int updated, TestCaseStatus status) {}

    // --- parameter sets (PRD-015) ------------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ParameterSet(UUID id, String name, Map<String, String> values) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ParameterSetList(List<ParameterSet> parameterSets, int total) {}

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

    /** An entry of the project's environment catalogue (PRD-032). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Environment(UUID id, String name, @Nullable String description) {}

    record EnvironmentList(List<Environment> environments) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TestRunPage(List<TestRunSummary> testRuns, int page, int size, long totalElements,
                       boolean hasMore) {}

    /**
     * @param id              the result's own id, which {@code record_test_result} needs whenever
     *                        {@code testCaseId} is ambiguous — see {@code parameterSetName}
     * @param parameterSetName present only for a parameterized case (PRD-015), which expands into
     *                        one result per set. That is exactly when one test case id maps to
     *                        several results and the agent has to address one by {@code id}
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TestResult(UUID id, UUID testCaseId, String testCaseTitle, TestResultStatus status,
                      @Nullable String comment, @Nullable String defectLink,
                      @Nullable String parameterSetName, @Nullable Long durationMs) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record TestRunDetail(UUID id, String key, String name, @Nullable String environment,
                         TestRunStatus status, @Nullable Instant startTime, @Nullable Instant endTime,
                         List<TestResult> results, @Nullable EffortSummary effort) {}

    // --- execution (write, PRD-027) ----------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CreatedTestRun(UUID id, String key, String name, TestRunStatus status,
                          int totalResults) {}

    /**
     * @param added true when no result for this test case existed in the run, so one was appended
     *              rather than an existing pending one filled in. Surfaced because it usually means
     *              the agent passed an id that is not in the run — worth it noticing
     * @param runStatus the run's status <em>after</em> the call: recording into a PLANNED run
     *                  starts it, so this is how the agent learns that happened
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RecordedResult(UUID resultId, UUID testCaseId, String testCaseTitle,
                          TestResultStatus status, boolean added, TestRunStatus runStatus) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CompletedTestRun(UUID id, String key, TestRunStatus status, int total, int passed,
                            int failed, int blocked, int skipped, int pending) {}

    /**
     * One outcome in a {@code record_test_results} call.
     *
     * <p>The {@code @Nullable}s are load-bearing exactly as they are on {@link Step}: victools
     * marks every property of a nested type required unless annotated, and
     * {@code @McpToolParam(required = false)} only reaches top-level parameters. Without them a
     * client is rejected at schema validation for omitting a comment — which most results have not
     * got — before any of this code runs. PRD-025 §8 found that the hard way.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ResultEntry(TestResultStatus status,
                       @Nullable UUID testCaseId,
                       @Nullable UUID resultId,
                       @Nullable String comment,
                       @Nullable String defectLink,
                       @Nullable Long durationMs) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RecordedResults(int recorded, TestRunStatus runStatus, int total, int passed, int failed,
                           int blocked, int skipped, int pending) {}

    /** @param stepNumber 1-based position within the result, which is how the agent addresses it */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record StepOutcome(int stepNumber, String action, TestResultStatus status,
                       @Nullable String actualResult) {}

    /**
     * @param resultStatus the parent result's status <em>after</em> the call — it is derived from
     *                     its steps (worst one wins), so the agent never sets it separately
     * @param steps        every step of the result, so the agent can see what is still PENDING
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RecordedStepResult(UUID resultId, UUID testCaseId, String testCaseTitle,
                              TestResultStatus resultStatus, TestRunStatus runStatus,
                              List<StepOutcome> steps) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record UpdatedTestRun(UUID id, String key, String name, @Nullable String environment,
                          TestRunStatus status, @Nullable UUID testPlanId) {}

    // --- comments ----------------------------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Comment(UUID id, String content, @Nullable String authorName, Instant createdAt) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CommentList(List<Comment> comments, int total) {}

    // --- bug reports (PRD-027) ---------------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record BugSummary(UUID id, String key, String title, BugReportStatus status,
                      @Nullable BugResolution resolution, Priority priority,
                      @Nullable String environment, @Nullable UUID testResultId,
                      @Nullable String testCaseTitle, @Nullable UUID testRunId,
                      @Nullable String testRunName, @Nullable String assigneeName) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record BugPage(List<BugSummary> bugReports, int page, int size, long totalElements,
                   boolean hasMore) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record BugDetail(UUID id, String key, String title, @Nullable String description,
                     @Nullable String stepsToReproduce, @Nullable String expectedBehavior,
                     @Nullable String actualBehavior, BugReportStatus status,
                     @Nullable BugResolution resolution, @Nullable String duplicateOfKey, Priority priority,
                     @Nullable String environment, @Nullable UUID testResultId,
                     @Nullable String testCaseTitle, @Nullable UUID testRunId,
                     @Nullable String testRunName, @Nullable String assigneeName,
                     @Nullable String reporterName, @Nullable Integer stepNumber,
                     List<BugOccurrence> occurrences) {}

    /** Where else the bug showed up (PRD-047); the found-in result is on the bug itself. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record BugOccurrence(UUID testResultId, String testRunKey, String testCaseKey, @Nullable Integer stepNumber) {}

    /** A near-match that blocked a bug create, so the agent can update it instead of re-filing. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DuplicateBug(UUID id, String key, String title, BugReportStatus status) {}

    /** What assign_bug_reports did (PRD-045 §3.5). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record BugAssignment(int assigned, @Nullable String assigneeName) {}

    // --- history ------------------------------------------------------------------------------

    /** @param current true for the live state of the case, which is always the highest number */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record VersionSummary(int versionNumber, Instant versionAt, String title, boolean current) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record VersionList(List<VersionSummary> versions, int total) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record VersionDetail(int versionNumber, Instant versionAt, String title,
                         @Nullable String description, @Nullable String preconditions,
                         Priority priority, TestCaseStatus status, @Nullable List<String> labels,
                         List<Step> steps) {}

    // --- reporting ----------------------------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PassRatePoint(UUID testRunId, String name, @Nullable Instant completedAt,
                         double passRate) {}

    /**
     * @param latestResultsByStatus the results of the most recently completed run only
     * @param overallPassRate       share of test cases whose most recent executed result, in a
     *                              completed run, is PASSED — each case counted once
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Dashboard(long totalTestCases, long totalTestSuites, long totalTestRuns,
                     long completedTestRuns, Map<String, Long> testCasesByStatus,
                     Map<String, Long> testCasesByPriority, Map<String, Long> latestResultsByStatus,
                     double overallPassRate, List<PassRatePoint> passRateTrend) {}

    /**
     * @param flakyScore proportion of consecutive PASSED/FAILED pairs that flipped, in [0,1]
     * @param failRate   proportion of the considered results that failed, in [0,1]
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record FlakyTest(UUID testCaseId, String testCaseKey, String title, double flakyScore,
                     double failRate, int runsConsidered) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record FlakyTestList(List<FlakyTest> flakyTests, int total) {}

    /** @param status absent when the case has never been executed */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SuiteCaseResult(UUID testCaseId, String testCaseTitle, @Nullable TestResultStatus status,
                           @Nullable UUID testRunId, @Nullable String testRunName) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SuiteReport(UUID id, String name, int total, int passed, int failed, int blocked,
                       int skipped, int untested, double passRate,
                       List<SuiteCaseResult> results) {}

    // --- pipelines ----------------------------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Workflow(UUID id, String name, String serverName, String provider,
                    @Nullable String defaultRef, @Nullable Map<String, String> defaultParameters) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record WorkflowList(List<Workflow> workflows, int total) {}

    /**
     * @param testRunKey present once the pipeline has reported results back, which is what turns
     *                   a pipeline run into a test run the other tools can read
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PipelineRun(UUID id, @Nullable UUID workflowId, String workflowName, String status,
                       @Nullable String externalUrl, @Nullable String triggeredRef,
                       @Nullable UUID testRunId, @Nullable String testRunKey,
                       @Nullable String errorMessage, Instant createdAt,
                       @Nullable Instant finishedAt) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PipelineRunPage(List<PipelineRun> pipelineRuns, int page, int size, long totalElements,
                           boolean hasMore) {}

    // --- external issues (PRD-010) ------------------------------------------------------------

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record IssueLink(UUID id, UUID testResultId, String provider, String externalId,
                     @Nullable String url, @Nullable String title, @Nullable String state) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record IssueLinkList(List<IssueLink> issues, int total) {}

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
