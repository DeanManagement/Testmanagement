package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TestCaseRepository extends JpaRepository<TestCase, UUID> {

    List<TestCase> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    long countByProjectId(UUID projectId);

    @Query("SELECT CAST(tc.status AS string), COUNT(tc) FROM TestCase tc WHERE tc.project.id = :projectId GROUP BY tc.status")
    List<Object[]> countByProjectIdGroupByStatus(@Param("projectId") UUID projectId);

    @Query("SELECT CAST(tc.priority AS string), COUNT(tc) FROM TestCase tc WHERE tc.project.id = :projectId GROUP BY tc.priority")
    List<Object[]> countByProjectIdGroupByPriority(@Param("projectId") UUID projectId);
}
