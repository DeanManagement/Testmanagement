package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.dto.attachment.AttachmentSummary;
import com.deanmanagement.testmanagement.project.internal.dto.attachment.StepFileRef;
import com.deanmanagement.testmanagement.project.internal.entity.Attachment;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    /** A constructor projection, so listing never reads the file bytes. */
    String SUMMARY = "SELECT new com.deanmanagement.testmanagement.project.internal.dto.attachment.AttachmentSummary("
            + "a.id, tc.id, b.id, ts.id, sr.id, a.fileName, a.contentType, a.sizeBytes, a.sha256, a.createdAt, a.createdBy) "
            + "FROM Attachment a LEFT JOIN a.testCase tc LEFT JOIN a.bugReport b "
            + "LEFT JOIN a.testStep ts LEFT JOIN a.stepResult sr ";

    /** The steps a case executes: its own, and those of the shared steps it references (PRD-030). */
    String STEPS_OF_CASE = "(ts.testCase.id = :testCaseId OR ts.sharedStep.id IN "
            + "(SELECT r.usesSharedStep.id FROM TestStep r WHERE r.testCase.id = :testCaseId)) ";

    @Query(SUMMARY + "WHERE tc.id = :testCaseId ORDER BY a.createdAt")
    List<AttachmentSummary> summariesByTestCase(@Param("testCaseId") UUID testCaseId);

    @Query(SUMMARY + "WHERE tc.id IN :testCaseIds ORDER BY a.createdAt")
    List<AttachmentSummary> summariesByTestCases(@Param("testCaseIds") Collection<UUID> testCaseIds);

    @Query(SUMMARY + "WHERE b.id = :bugReportId ORDER BY a.createdAt")
    List<AttachmentSummary> summariesByBugReport(@Param("bugReportId") UUID bugReportId);

    @Query(SUMMARY + "WHERE ts.id = :testStepId ORDER BY a.createdAt")
    List<AttachmentSummary> summariesByTestStep(@Param("testStepId") UUID testStepId);

    @Query(SUMMARY + "WHERE " + STEPS_OF_CASE + "ORDER BY a.createdAt")
    List<AttachmentSummary> summariesOnStepsOfCase(@Param("testCaseId") UUID testCaseId);

    @Query(SUMMARY + "WHERE ts.id IN :testStepIds ORDER BY a.createdAt")
    List<AttachmentSummary> summariesByTestSteps(@Param("testStepIds") Collection<UUID> testStepIds);

    @Query(SUMMARY + "WHERE sr.id = :stepResultId ORDER BY a.createdAt")
    List<AttachmentSummary> summariesByStepResult(@Param("stepResultId") UUID stepResultId);

    /** A run's evidence, and the documents on every step it executes: what the run view shows. */
    @Query(SUMMARY + "LEFT JOIN sr.testResult r "
            + "WHERE r.testRun.id = :testRunId OR ts.id IN "
            + "(SELECT x.testStep.id FROM StepResult x WHERE x.testResult.testRun.id = :testRunId) ORDER BY a.createdAt")
    List<AttachmentSummary> summariesForRun(@Param("testRunId") UUID testRunId);

    long countByTestCaseId(UUID testCaseId);

    long countByBugReportId(UUID bugReportId);

    long countByTestStepId(UUID testStepId);

    long countByStepResultId(UUID stepResultId);

    /**
     * Every owner counts toward one project quota (PRD-051 §3.2, TM05). Explicit outer joins: an
     * implicit path through a null owner would be an inner join and drop the other owners' rows.
     */
    @Query("SELECT COALESCE(SUM(a.sizeBytes), 0) FROM Attachment a LEFT JOIN a.testCase tc LEFT JOIN a.bugReport b "
            + "LEFT JOIN a.testStep ts LEFT JOIN ts.testCase stc LEFT JOIN ts.sharedStep ss "
            + "LEFT JOIN a.stepResult sr LEFT JOIN sr.testResult r LEFT JOIN r.testRun run "
            + "WHERE tc.project.id = :projectId OR b.project.id = :projectId "
            + "OR stc.project.id = :projectId OR ss.project.id = :projectId OR run.project.id = :projectId")
    long totalBytesInProject(@Param("projectId") UUID projectId);

    Optional<Attachment> findByIdAndTestCaseId(UUID id, UUID testCaseId);

    /** A file on the case itself or on one of the steps it executes. */
    @Query("SELECT a FROM Attachment a LEFT JOIN a.testStep ts WHERE a.id = :id "
            + "AND (a.testCase.id = :testCaseId OR " + STEPS_OF_CASE + ")")
    Optional<Attachment> findOnCaseOrItsSteps(@Param("id") UUID id, @Param("testCaseId") UUID testCaseId);

    List<Attachment> findByTestCaseId(UUID testCaseId);

    Optional<Attachment> findByIdAndBugReportId(UUID id, UUID bugReportId);

    Optional<Attachment> findByIdAndTestStepId(UUID id, UUID testStepId);

    List<Attachment> findByTestStepIdIn(Collection<UUID> testStepIds);

    Optional<Attachment> findByIdAndStepResultId(UUID id, UUID stepResultId);

    /** Every evidence file of a result, in step order (PRD-051 §3.4). */
    @Query("SELECT a FROM Attachment a JOIN FETCH a.stepResult sr WHERE sr.testResult.id = :testResultId "
            + "ORDER BY sr.position, a.createdAt")
    List<Attachment> findEvidenceOfResult(@Param("testResultId") UUID testResultId);

    @Query("SELECT new com.deanmanagement.testmanagement.project.internal.dto.attachment.StepFileRef(a.id, a.testStep.id) "
            + "FROM Attachment a WHERE a.testStep.id IN :testStepIds")
    List<StepFileRef> refsOnSteps(@Param("testStepIds") Collection<UUID> testStepIds);

    /** Parks step files on their case while the steps are replaced (TM06): one owner, no step row needed. */
    @Modifying
    @Query("UPDATE Attachment a SET a.testCase = :testCase, a.testStep = null WHERE a.id IN :ids")
    int parkOnCase(@Param("ids") Collection<UUID> ids, @Param("testCase") TestCase testCase);

    @Modifying
    @Query("UPDATE Attachment a SET a.testStep = :testStep, a.testCase = null WHERE a.id IN :ids")
    int moveToStep(@Param("ids") Collection<UUID> ids, @Param("testStep") TestStep testStep);

    @Modifying
    @Query("DELETE FROM Attachment a WHERE a.id IN :ids")
    int deleteByIds(@Param("ids") Collection<UUID> ids);
}
