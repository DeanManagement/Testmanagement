package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.BugReport;
import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BugReportRepository extends JpaRepository<BugReport, UUID> {

    @Query("SELECT b FROM BugReport b " +
            "LEFT JOIN FETCH b.assignee " +
            "LEFT JOIN FETCH b.testResult tr " +
            "LEFT JOIN FETCH tr.testCase " +
            "LEFT JOIN FETCH b.testRun " +
            "WHERE b.project.id = :projectId " +
            "ORDER BY b.createdAt DESC")
    List<BugReport> findByProjectIdWithDetails(@Param("projectId") UUID projectId);

    @Query("SELECT b FROM BugReport b " +
            "LEFT JOIN FETCH b.assignee " +
            "LEFT JOIN FETCH b.testResult tr " +
            "LEFT JOIN FETCH tr.testCase " +
            "LEFT JOIN FETCH b.testRun " +
            "WHERE b.id = :id AND b.project.id = :projectId")
    Optional<BugReport> findByIdAndProjectIdWithDetails(@Param("id") UUID id, @Param("projectId") UUID projectId);

    @Query("SELECT b FROM BugReport b " +
            "LEFT JOIN FETCH b.assignee " +
            "LEFT JOIN FETCH b.testResult tr " +
            "LEFT JOIN FETCH tr.testCase " +
            "LEFT JOIN FETCH b.testRun " +
            "WHERE b.testResult.id = :testResultId AND b.project.id = :projectId")
    List<BugReport> findByTestResultIdAndProjectId(@Param("testResultId") UUID testResultId, @Param("projectId") UUID projectId);

    @Query("SELECT b FROM BugReport b " +
            "LEFT JOIN FETCH b.assignee " +
            "LEFT JOIN FETCH b.testResult tr " +
            "LEFT JOIN FETCH tr.testCase " +
            "LEFT JOIN FETCH b.testRun " +
            "JOIN FETCH b.project " +
            "WHERE b.assignee.id = :assigneeId " +
            "ORDER BY b.createdAt DESC")
    List<BugReport> findByAssigneeIdWithDetails(@Param("assigneeId") UUID assigneeId);

    /**
     * Bug reports the user filed (matched by {@code createdBy}) that are still
     * open and have not been touched recently. Ordered by {@code updatedAt}
     * ascending so the stalest items are reported first. Used by the "My
     * queue" dashboard widget.
     */
    @Query("SELECT b FROM BugReport b " +
            "JOIN FETCH b.project " +
            "WHERE b.createdBy = :userId " +
            "AND b.status IN :statuses " +
            "AND b.updatedAt < :staleBefore " +
            "ORDER BY b.updatedAt ASC")
    List<BugReport> findStaleByCreatedBy(@Param("userId") UUID userId,
                                         @Param("statuses") Collection<BugReportStatus> statuses,
                                         @Param("staleBefore") Instant staleBefore,
                                         Pageable pageable);
}
