package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.TestResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface TestResultRepository extends JpaRepository<TestResult, UUID> {

    @Query("SELECT r FROM TestResult r JOIN FETCH r.testCase JOIN FETCH r.testRun run " +
           "WHERE r.testCase.id IN :testCaseIds AND run.project.id = :projectId " +
           "AND run.status = 'COMPLETED' ORDER BY r.updatedAt DESC")
    List<TestResult> findByTestCaseIdsAndCompletedRuns(
            @Param("testCaseIds") Set<UUID> testCaseIds,
            @Param("projectId") UUID projectId);

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END FROM TestResult r WHERE r.testCase.id IN :testCaseIds")
    boolean existsByTestCaseIdIn(@Param("testCaseIds") Set<UUID> testCaseIds);
}
