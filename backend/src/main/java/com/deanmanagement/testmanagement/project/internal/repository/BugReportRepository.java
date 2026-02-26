package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.BugReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
