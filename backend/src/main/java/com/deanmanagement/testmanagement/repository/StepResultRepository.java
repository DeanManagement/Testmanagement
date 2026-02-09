package com.deanmanagement.testmanagement.repository;

import com.deanmanagement.testmanagement.entity.StepResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StepResultRepository extends JpaRepository<StepResult, UUID> {
}
