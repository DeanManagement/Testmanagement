package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.BugReport;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BugReportRepository extends JpaRepository<BugReport, UUID>, JpaSpecificationExecutor<BugReport> {

    /** Release-gate blockers (PRD-037): the project's bugs at a priority, in any of these statuses. */
    long countByProjectIdAndPriorityAndStatusIn(UUID projectId, Priority priority, Collection<BugReportStatus> statuses);

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

    java.util.List<BugReport> findByExploratorySessionIdOrderByCreatedAtAsc(UUID sessionId);

    Optional<BugReport> findByKeyAndProjectId(String key, UUID projectId);

    /** PRD-047 dashboard: [status, count] rows. */
    @Query("SELECT b.status, COUNT(b) FROM BugReport b WHERE b.project.id = :projectId GROUP BY b.status")
    List<Object[]> countByStatus(@Param("projectId") UUID projectId);

    /** PRD-047 dashboard: [priority, count] rows for bugs in the given statuses. */
    @Query("SELECT b.priority, COUNT(b) FROM BugReport b WHERE b.project.id = :projectId AND b.status IN :statuses "
            + "GROUP BY b.priority")
    List<Object[]> countByPriorityAndStatusIn(@Param("projectId") UUID projectId,
                                              @Param("statuses") Collection<BugReportStatus> statuses);

    @Query("SELECT b.createdAt FROM BugReport b WHERE b.project.id = :projectId AND b.createdAt >= :since")
    List<Instant> findCreatedAtSince(@Param("projectId") UUID projectId, @Param("since") Instant since);

    @Query("SELECT b.resolvedAt FROM BugReport b WHERE b.project.id = :projectId AND b.resolvedAt >= :since")
    List<Instant> findResolvedAtSince(@Param("projectId") UUID projectId, @Param("since") Instant since);

    /**
     * PRD-047: bugs found in a run of the plan, or linked to a result of one; each once, newest first.
     * Explicit left joins, since a bug without a run must not drop out of the OR.
     */
    @Query("SELECT DISTINCT b FROM BugReport b LEFT JOIN b.testRun r LEFT JOIN FETCH b.assignee "
            + "WHERE b.project.id = :projectId AND (r.testPlan.id = :planId OR EXISTS ("
            + "SELECT l.id FROM BugReportLink l JOIN l.testResult lr JOIN lr.testRun lrun "
            + "WHERE l.bugReport = b AND lrun.testPlan.id = :planId)) "
            + "ORDER BY b.createdAt DESC")
    List<BugReport> findByTestPlan(@Param("projectId") UUID projectId, @Param("planId") UUID planId);

    /** Bulk operations (PRD-045): resolves ids within the project only, so a foreign id is simply absent. */
    List<BugReport> findByIdInAndProjectId(Collection<UUID> ids, UUID projectId);
}
