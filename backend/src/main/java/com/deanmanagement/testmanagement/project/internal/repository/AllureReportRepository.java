package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.dto.testrun.RunAllureReportId;
import com.deanmanagement.testmanagement.project.internal.entity.AllureReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AllureReportRepository extends JpaRepository<AllureReport, UUID> {
    Optional<AllureReport> findByTestRunId(UUID testRunId);

    @Query("SELECT new com.deanmanagement.testmanagement.project.internal.dto.testrun.RunAllureReportId(" +
           "a.testRun.id, a.id) FROM AllureReport a WHERE a.testRun.id IN :runIds")
    List<RunAllureReportId> findIdsByTestRunIds(@Param("runIds") Collection<UUID> runIds);

    Optional<AllureReport> findByTestRunKey(String testRunKey);

    boolean existsByTestRunId(UUID testRunId);

    void deleteByTestRunId(UUID testRunId);
}
