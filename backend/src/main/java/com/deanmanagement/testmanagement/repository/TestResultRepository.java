package com.deanmanagement.testmanagement.repository;

import com.deanmanagement.testmanagement.entity.TestResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TestResultRepository extends JpaRepository<TestResult, UUID> {
}
