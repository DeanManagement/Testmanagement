package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.dto.analytics.FlakyResultRow;
import com.deanmanagement.testmanagement.project.internal.dto.testrun.RunStatusCount;
import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface TestResultRepository extends JpaRepository<TestResult, UUID> {

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
     * Terminal results across a project, newest first, for flakiness scoring (PRD-016).
     *
     * <p>Only PASSED and FAILED count: BLOCKED, SKIPPED and PENDING say something about the
     * environment or the schedule, not about the test flip-flopping. Aborted runs are excluded so a
     * run someone cut short does not read as a transition.
     *
     * <p>Ordered by when the run happened rather than when the row was written, since results are
     * often backfilled by CI ingestion long after the run started.
     */
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

    @Query("SELECT new com.deanmanagement.testmanagement.project.internal.dto.testrun.RunStatusCount(" +
           "r.testRun.id, r.status, COUNT(r)) " +
           "FROM TestResult r WHERE r.testRun.id IN :runIds GROUP BY r.testRun.id, r.status")
    List<RunStatusCount> countStatusByRunIds(@Param("runIds") Collection<UUID> runIds);
}
