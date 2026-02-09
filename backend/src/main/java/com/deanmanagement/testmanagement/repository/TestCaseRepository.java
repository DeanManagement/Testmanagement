package com.deanmanagement.testmanagement.repository;

import com.deanmanagement.testmanagement.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TestCaseRepository extends JpaRepository<TestCase, UUID> {

    List<TestCase> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
}
