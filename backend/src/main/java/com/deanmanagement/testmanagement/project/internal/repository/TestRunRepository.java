package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestRunRepository extends JpaRepository<TestRun, UUID>, JpaSpecificationExecutor<TestRun> {

    @Query("SELECT DISTINCT r FROM TestRun r " +
            "LEFT JOIN FETCH r.executor " +
            "LEFT JOIN FETCH r.completedBy " +
            "LEFT JOIN FETCH r.testPlan " +
            "WHERE r.project.id = :projectId " +
            "ORDER BY r.createdAt DESC")
    List<TestRun> findByProjectIdOrderByCreatedAtDesc(@Param("projectId") UUID projectId);

    @Query("SELECT r FROM TestRun r " +
            "LEFT JOIN FETCH r.executor " +
            "LEFT JOIN FETCH r.completedBy " +
            "LEFT JOIN FETCH r.testPlan " +
            "WHERE r.key = :key")
    Optional<TestRun> findByKey(@Param("key") String key);

    long countByProjectId(UUID projectId);

    long countByProjectIdAndStatus(UUID projectId, TestRunStatus status);

    @Query("SELECT DISTINCT r FROM TestRun r " +
            "LEFT JOIN FETCH r.executor " +
            "LEFT JOIN FETCH r.completedBy " +
            "LEFT JOIN FETCH r.testPlan " +
            "WHERE r.project.id = :projectId " +
            "ORDER BY r.createdAt DESC")
    List<TestRun> findTop5ByProjectIdOrderByCreatedAtDesc(@Param("projectId") UUID projectId);

    @Query("SELECT DISTINCT r FROM TestRun r " +
            "LEFT JOIN FETCH r.executor " +
            "LEFT JOIN FETCH r.completedBy " +
            "LEFT JOIN FETCH r.testPlan " +
            "WHERE r.project.id = :projectId " +
            "AND r.status = :status " +
            "ORDER BY r.endTime DESC")
    List<TestRun> findTop10ByProjectIdAndStatusOrderByEndTimeDesc(
            @Param("projectId") UUID projectId,
            @Param("status") TestRunStatus status);

    @Query("SELECT r FROM TestRun r " +
            "JOIN FETCH r.project " +
            "LEFT JOIN FETCH r.executor " +
            "LEFT JOIN FETCH r.completedBy " +
            "LEFT JOIN FETCH r.testPlan " +
            "WHERE r.executor.id = :executorId " +
            "ORDER BY r.createdAt DESC")
    List<TestRun> findByExecutorIdWithProject(@Param("executorId") UUID executorId);

    @Query("SELECT r FROM TestRun r " +
            "JOIN FETCH r.project " +
            "LEFT JOIN FETCH r.executor " +
            "LEFT JOIN FETCH r.completedBy " +
            "LEFT JOIN FETCH r.testPlan " +
            "WHERE r.executor.id = :executorId " +
            "AND r.status IN :statuses " +
            "ORDER BY r.createdAt DESC")
    List<TestRun> findByExecutorIdAndStatusInWithProject(
            @Param("executorId") UUID executorId,
            @Param("statuses") List<TestRunStatus> statuses);

    /**
     * Test runs whose executor is the given user and whose status is
     * {@code IN_PROGRESS}, most recently updated first. Used by the "My queue"
     * widget to surface unfinished work the user started.
     */
    @Query("SELECT r FROM TestRun r " +
            "JOIN FETCH r.project " +
            "WHERE r.executor.id = :executorId " +
            "AND r.status = com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus.IN_PROGRESS " +
            "ORDER BY r.updatedAt DESC")
    List<TestRun> findInProgressByExecutor(@Param("executorId") UUID executorId, Pageable pageable);
}
