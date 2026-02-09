package com.deanmanagement.testmanagement.repository;

import com.deanmanagement.testmanagement.entity.TestRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TestRunRepository extends JpaRepository<TestRun, UUID> {

    List<TestRun> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
