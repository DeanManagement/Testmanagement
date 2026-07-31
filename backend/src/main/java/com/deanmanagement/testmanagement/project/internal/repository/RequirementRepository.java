package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.Requirement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RequirementRepository extends JpaRepository<Requirement, UUID> {

    Page<Requirement> findByProjectIdOrderByExternalIdAsc(UUID projectId, Pageable pageable);

    List<Requirement> findByProjectIdOrderByExternalIdAsc(UUID projectId);

    Optional<Requirement> findByIdAndProjectId(UUID id, UUID projectId);

    boolean existsByProjectIdAndExternalId(UUID projectId, String externalId);

    /** Fetches linked cases eagerly; the matrix needs them all and would otherwise N+1. */
    @Query("SELECT DISTINCT r FROM Requirement r LEFT JOIN FETCH r.testCases WHERE r.id IN :ids")
    List<Requirement> findWithTestCases(@Param("ids") List<UUID> ids);

    long countByProjectId(UUID projectId);
}
