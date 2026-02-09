package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.StepResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StepResultRepository extends JpaRepository<StepResult, UUID> {
}
