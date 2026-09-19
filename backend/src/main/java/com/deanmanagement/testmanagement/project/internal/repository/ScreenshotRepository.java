package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.Screenshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScreenshotRepository extends JpaRepository<Screenshot, UUID> {
    Optional<Screenshot> findByStepResultId(UUID stepResultId);

    /** Every step screenshot of a result, in step order (PRD-051 §3.4). */
    @Query("SELECT s FROM Screenshot s JOIN FETCH s.stepResult sr WHERE sr.testResult.id = :testResultId "
            + "ORDER BY sr.position")
    List<Screenshot> findByTestResultId(@Param("testResultId") UUID testResultId);
}
