package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.StepImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StepImageRepository extends JpaRepository<StepImage, UUID> {
    Optional<StepImage> findByTestStepId(UUID testStepId);
}
