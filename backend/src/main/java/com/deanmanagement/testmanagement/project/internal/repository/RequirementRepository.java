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

    Optional<Requirement> findByProjectIdAndExternalId(UUID projectId, String externalId);

    /** {@code pattern} comes from {@code LikePatterns.containing}; matches the external id or the title. */
    @Query("SELECT r FROM Requirement r WHERE r.projectId = :projectId "
            + "AND (LOWER(r.externalId) LIKE :pattern ESCAPE '!' OR LOWER(r.title) LIKE :pattern ESCAPE '!') "
            + "ORDER BY r.externalId")
    Page<Requirement> search(@Param("projectId") UUID projectId, @Param("pattern") String pattern,
                             Pageable pageable);

    /** The requirements a test case is linked to (TM08). */
    @Query("SELECT r FROM Requirement r JOIN r.testCases tc "
            + "WHERE r.projectId = :projectId AND tc.id = :testCaseId ORDER BY r.externalId")
    List<Requirement> findLinkedTo(@Param("projectId") UUID projectId, @Param("testCaseId") UUID testCaseId);

    /** Fetches linked cases eagerly; the matrix needs them all and would otherwise N+1. */
    @Query("SELECT DISTINCT r FROM Requirement r LEFT JOIN FETCH r.testCases WHERE r.id IN :ids")
    List<Requirement> findWithTestCases(@Param("ids") List<UUID> ids);

    long countByProjectId(UUID projectId);
}
