package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.dto.environment.EnvironmentResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.analytics.FlakyResultRow;
import com.deanmanagement.testmanagement.project.internal.dto.comparison.ComparableResult;
import com.deanmanagement.testmanagement.project.internal.dto.effort.BurnDownRow;
import com.deanmanagement.testmanagement.project.internal.dto.readiness.ReadinessResultRow;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.RunStatusCount;
import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface TestResultRepository extends JpaRepository<TestResult, UUID> {

    /**
     * Every executed result of the project's completed runs as {@code [testCaseId, status]},
     * newest first, so the first row seen for a test case is its current outcome. PENDING rows are
     * left out: a result nobody executed is not an outcome, and would otherwise hide the real one
     * from an earlier run.
     */
    // ponytail: scans every executed result of the project per dashboard load; move the
    // "latest per case" pick into SQL (window function) if a project's history makes this slow.
    @Query("SELECT r.testCase.id, r.status FROM TestResult r JOIN r.testRun run " +
           "WHERE run.project.id = :projectId AND run.status = 'COMPLETED' " +
           "AND r.status <> 'PENDING' ORDER BY r.updatedAt DESC")
    List<Object[]> findExecutedOutcomesOfCompletedRunsNewestFirst(@Param("projectId") UUID projectId);

    @Query("SELECT r FROM TestResult r JOIN FETCH r.testCase JOIN FETCH r.testRun run " +
           "WHERE r.testCase.id IN :testCaseIds AND run.project.id = :projectId " +
           "AND run.status = 'COMPLETED' ORDER BY r.updatedAt DESC")
    List<TestResult> findByTestCaseIdsAndCompletedRuns(
            @Param("testCaseIds") Set<UUID> testCaseIds,
            @Param("projectId") UUID projectId);

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM TestResult r WHERE r.testCase.id IN :testCaseIds")
    boolean existsByTestCaseIdIn(@Param("testCaseIds") Set<UUID> testCaseIds);

    @Query("SELECT r FROM TestResult r LEFT JOIN FETCH r.stepResults WHERE r.id IN :ids AND r.testRun.id = :runId")
    List<TestResult> findByIdInAndTestRunId(@Param("ids") Set<UUID> ids, @Param("runId") UUID runId);

    /**
     * Project-scoped lookup for a caller-supplied result id (PRD-027 §3.5).
     *
     * <p>A test result carries no project of its own — it reaches one only through its run, which
     * is why a bare {@code findById} looked harmless here and was not. A bug report linked to
     * another project's result echoes that result's test case title straight back to the caller.
     */
    @Query("SELECT r FROM TestResult r JOIN r.testRun run "
           + "WHERE r.id = :id AND run.project.id = :projectId")
    Optional<TestResult> findByIdAndProjectId(@Param("id") UUID id, @Param("projectId") UUID projectId);

    /**
     * Locks the result row, so concurrent updates of its steps derive its status one after another
     * from committed sibling states (TES-BUG-17).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM TestResult r WHERE r.id = :id")
    Optional<TestResult> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Terminal results across a project, newest first, for flakiness scoring (PRD-016).
     *
     * <p>Only PASSED and FAILED count: BLOCKED, SKIPPED and PENDING say something about the
     * environment or the schedule, not about the test flip-flopping. Aborted runs are excluded so a
     * run someone cut short does not read as a transition.
     *
     * <p>Ordered by when the run happened rather than when the row was written, since results are
     * often backfilled by CI ingestion long after the run started.
     */
    /**
     * A case's executed results, newest first by run time rather than insert time, since CI
     * backfills results late (same ordering as the flaky query below). PENDING results are left
     * out so a freshly planned run doesn't hide the last real outcome.
     */
    @Query("""
           SELECT new com.deanmanagement.testmanagement.project.internal.dto.environment.EnvironmentResultResponse(
               env.id, env.name, r.status, run.id, run.key,
               COALESCE(run.endTime, run.startTime, run.createdAt))
           FROM TestResult r
           JOIN r.testRun run
           LEFT JOIN run.projectEnvironment env
           WHERE r.testCase.id = :testCaseId
             AND run.project.id = :projectId
             AND run.status <> 'ABORTED'
             AND r.status <> 'PENDING'
           ORDER BY COALESCE(run.endTime, run.startTime, run.createdAt) DESC
           """)
    List<EnvironmentResultResponse> findExecutedResultsNewestFirst(@Param("projectId") UUID projectId, @Param("testCaseId") UUID testCaseId);

    @Query("""
           SELECT new com.deanmanagement.testmanagement.project.internal.dto.analytics.FlakyResultRow(
               tc.id, tc.key, tc.title, r.status,
               COALESCE(run.endTime, run.startTime, run.createdAt))
           FROM TestResult r
           JOIN r.testCase tc
           JOIN r.testRun run
           WHERE run.project.id = :projectId
             AND run.status <> 'ABORTED'
             AND r.status IN ('PASSED', 'FAILED')
           ORDER BY tc.id ASC, COALESCE(run.endTime, run.startTime, run.createdAt) DESC
           """)
    List<FlakyResultRow> findTerminalResultsForFlakiness(@Param("projectId") UUID projectId);

    /** Run comparison input (PRD-038): one run's results, without steps or screenshots. */
    @Query("""
           SELECT new com.deanmanagement.testmanagement.project.internal.dto.comparison.ComparableResult(
               r.id, tc.id, tc.key, tc.title, r.parameterSetName, r.status, r.executedVersion)
           FROM TestResult r
           JOIN r.testCase tc
           WHERE r.testRun.id = :runId
           """)
    List<ComparableResult> findComparableResults(@Param("runId") UUID runId);

    /** Release-gate input (PRD-037): every result of the plan's non-aborted runs, with when its run happened. */
    @Query("""
           SELECT new com.deanmanagement.testmanagement.project.internal.dto.readiness.ReadinessResultRow(
               r.testCase.id, r.parameterSetName, r.status,
               COALESCE(run.endTime, run.startTime, run.createdAt), r.updatedAt)
           FROM TestResult r
           JOIN r.testRun run
           WHERE run.testPlan.id = :planId
             AND run.status <> 'ABORTED'
           """)
    List<ReadinessResultRow> findReadinessRows(@Param("planId") UUID planId);

    /** Burn-down input (PRD-036): aborted runs are excluded, as in the plan's effort summary. */
    @Query("""
           SELECT new com.deanmanagement.testmanagement.project.internal.dto.effort.BurnDownRow(
               r.createdAt, r.executedAt, r.status, tc.estimateMinutes)
           FROM TestResult r
           JOIN r.testCase tc
           JOIN r.testRun run
           WHERE run.testPlan.id = :planId
             AND run.status <> 'ABORTED'
           """)
    List<BurnDownRow> findBurnDownRows(@Param("planId") UUID planId);

    /** The newest executions of a case that measured a duration, for its median actual (PRD-036). */
    List<TestResult> findTop5ByTestCaseIdAndDurationMsNotNullAndExecutedAtNotNullOrderByExecutedAtDesc(UUID testCaseId);

    @Query("SELECT new com.deanmanagement.testmanagement.project.internal.dto.testrun.RunStatusCount(" +
           "r.testRun.id, r.status, COUNT(r)) " +
           "FROM TestResult r WHERE r.testRun.id IN :runIds GROUP BY r.testRun.id, r.status")
    List<RunStatusCount> countStatusByRunIds(@Param("runIds") Collection<UUID> runIds);

    /**
     * PRD-050: every result of a case, newest run first; one row per parameter set. The count query
     * carries no fetch join, which paging requires.
     */
    @Query(value = "SELECT r FROM TestResult r JOIN FETCH r.testRun run "
            + "WHERE r.testCase.id = :testCaseId AND run.project.id = :projectId "
            + "ORDER BY run.createdAt DESC, r.parameterSetName ASC",
            countQuery = "SELECT COUNT(r) FROM TestResult r "
                    + "WHERE r.testCase.id = :testCaseId AND r.testRun.project.id = :projectId")
    Page<TestResult> findHistory(@Param("projectId") UUID projectId, @Param("testCaseId") UUID testCaseId,
                                 Pageable pageable);
}
