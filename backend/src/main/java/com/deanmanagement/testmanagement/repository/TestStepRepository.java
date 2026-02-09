package com.deanmanagement.testmanagement.repository;

import com.deanmanagement.testmanagement.entity.TestStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TestStepRepository extends JpaRepository<TestStep, UUID> {
}
