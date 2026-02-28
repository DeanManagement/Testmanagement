package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.TestPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TestPlanRepository extends JpaRepository<TestPlan, UUID> {

    List<TestPlan> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    List<TestPlan> findByAssigneeIdOrderByCreatedAtDesc(UUID assigneeId);

    long countByProjectId(UUID projectId);
}
