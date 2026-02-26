package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.entity.TestRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
