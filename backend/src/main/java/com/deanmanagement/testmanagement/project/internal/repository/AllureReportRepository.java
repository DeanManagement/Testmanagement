package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.AllureReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AllureReportRepository extends JpaRepository<AllureReport, UUID> {
    Optional<AllureReport> findByTestRunId(UUID testRunId);

    boolean existsByTestRunId(UUID testRunId);

    void deleteByTestRunId(UUID testRunId);
}
