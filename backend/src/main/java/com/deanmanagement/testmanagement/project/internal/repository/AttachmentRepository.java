package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.dto.attachment.AttachmentSummary;
import com.deanmanagement.testmanagement.project.internal.entity.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {

    /** A constructor projection, so listing never reads the file bytes. */
    String SUMMARY = "SELECT new com.deanmanagement.testmanagement.project.internal.dto.attachment.AttachmentSummary("
            + "a.id, tc.id, b.id, a.fileName, a.contentType, a.sizeBytes, a.sha256, a.createdAt, a.createdBy) "
            + "FROM Attachment a LEFT JOIN a.testCase tc LEFT JOIN a.bugReport b ";

    @Query(SUMMARY + "WHERE tc.id = :testCaseId ORDER BY a.createdAt")
    List<AttachmentSummary> summariesByTestCase(@Param("testCaseId") UUID testCaseId);

    @Query(SUMMARY + "WHERE tc.id IN :testCaseIds ORDER BY a.createdAt")
    List<AttachmentSummary> summariesByTestCases(@Param("testCaseIds") Collection<UUID> testCaseIds);

    @Query(SUMMARY + "WHERE b.id = :bugReportId ORDER BY a.createdAt")
    List<AttachmentSummary> summariesByBugReport(@Param("bugReportId") UUID bugReportId);

    long countByTestCaseId(UUID testCaseId);

    long countByBugReportId(UUID bugReportId);

    /** Both owners count toward one project quota (PRD-051 §3.2). */
    @Query("SELECT COALESCE(SUM(a.sizeBytes), 0) FROM Attachment a LEFT JOIN a.testCase tc LEFT JOIN a.bugReport b "
            + "WHERE tc.project.id = :projectId OR b.project.id = :projectId")
    long totalBytesInProject(@Param("projectId") UUID projectId);

    Optional<Attachment> findByIdAndTestCaseId(UUID id, UUID testCaseId);

    Optional<Attachment> findByIdAndBugReportId(UUID id, UUID bugReportId);
}
