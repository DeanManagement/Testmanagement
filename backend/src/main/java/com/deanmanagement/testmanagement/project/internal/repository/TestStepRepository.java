package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.TestStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TestStepRepository extends JpaRepository<TestStep, UUID> {
}
