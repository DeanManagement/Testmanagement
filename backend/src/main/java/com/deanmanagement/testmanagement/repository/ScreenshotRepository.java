package com.deanmanagement.testmanagement.repository;

import com.deanmanagement.testmanagement.entity.Screenshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ScreenshotRepository extends JpaRepository<Screenshot, UUID> {
    Optional<Screenshot> findByStepResultId(UUID stepResultId);
}
