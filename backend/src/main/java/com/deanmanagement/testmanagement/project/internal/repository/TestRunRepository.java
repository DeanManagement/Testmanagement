package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestRunRepository extends JpaRepository<TestRun, UUID> {

    List<TestRun> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    Optional<TestRun> findByKey(String key);

    long countByProjectId(UUID projectId);

    long countByProjectIdAndStatus(UUID projectId, TestRunStatus status);

    List<TestRun> findTop5ByProjectIdOrderByCreatedAtDesc(UUID projectId);

    List<TestRun> findTop10ByProjectIdAndStatusOrderByEndTimeDesc(UUID projectId, TestRunStatus status);

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
}
