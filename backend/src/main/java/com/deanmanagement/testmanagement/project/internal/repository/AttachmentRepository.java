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
    @Query("SELECT new com.deanmanagement.testmanagement.project.internal.dto.attachment.AttachmentSummary("
            + "a.id, a.testCase.id, a.fileName, a.contentType, a.sizeBytes, a.sha256, a.createdAt, a.createdBy) "
            + "FROM Attachment a WHERE a.testCase.id = :testCaseId ORDER BY a.createdAt")
    List<AttachmentSummary> summariesByTestCase(@Param("testCaseId") UUID testCaseId);

    @Query("SELECT new com.deanmanagement.testmanagement.project.internal.dto.attachment.AttachmentSummary("
            + "a.id, a.testCase.id, a.fileName, a.contentType, a.sizeBytes, a.sha256, a.createdAt, a.createdBy) "
            + "FROM Attachment a WHERE a.testCase.id IN :testCaseIds ORDER BY a.createdAt")
    List<AttachmentSummary> summariesByTestCases(@Param("testCaseIds") Collection<UUID> testCaseIds);


    long countByTestCaseId(UUID testCaseId);

    @Query("SELECT COALESCE(SUM(a.sizeBytes), 0) FROM Attachment a WHERE a.testCase.project.id = :projectId")
    long totalBytesInProject(@Param("projectId") UUID projectId);

    Optional<Attachment> findByIdAndTestCaseId(UUID id, UUID testCaseId);
}
