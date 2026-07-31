package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.TestSuite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.UUID;

public interface TestSuiteRepository extends JpaRepository<TestSuite, UUID>, JpaSpecificationExecutor<TestSuite> {

    List<TestSuite> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    long countByProjectId(UUID projectId);
}
