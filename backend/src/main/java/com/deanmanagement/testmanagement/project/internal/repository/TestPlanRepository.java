package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.TestPlan;
import com.deanmanagement.testmanagement.project.internal.entity.TestPlanStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestPlanRepository extends JpaRepository<TestPlan, UUID> {

    List<TestPlan> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    /**
     * Project-scoped lookup for a caller-supplied plan id (PRD-027 §3.5).
     *
     * <p>Callers that already know the project must not reach a plan through a bare
     * {@code findById}: attaching a run to another project's plan makes that run count toward the
     * other plan's pass rate, which is a cross-project write, not just a leak.
     */
    Optional<TestPlan> findByIdAndProjectId(UUID id, UUID projectId);

    List<TestPlan> findByAssigneeIdOrderByCreatedAtDesc(UUID assigneeId);

    long countByProjectId(UUID projectId);

    /**
     * Test plans assigned to a user that have a target date on or before the
     * supplied cutoff and are still open or in progress. Used by the "My queue"
     * widget — order by {@code targetDate} ascending so the earliest deadlines
     * surface first (including overdue ones, which sort to the very top).
     */
    @Query("SELECT tp FROM TestPlan tp " +
            "JOIN FETCH tp.project " +
            "WHERE tp.assignee.id = :userId " +
            "AND tp.status IN :statuses " +
            "AND tp.targetDate IS NOT NULL " +
            "AND tp.targetDate <= :latestTargetDate " +
            "ORDER BY tp.targetDate ASC")
    List<TestPlan> findDueByAssignee(@Param("userId") UUID userId,
                                     @Param("statuses") Collection<TestPlanStatus> statuses,
                                     @Param("latestTargetDate") LocalDate latestTargetDate,
                                     Pageable pageable);
}
