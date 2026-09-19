package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.SharedStep;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SharedStepRepository extends JpaRepository<SharedStep, UUID> {

    Optional<SharedStep> findByIdAndProjectId(UUID id, UUID projectId);

    boolean existsByProjectIdAndTitle(UUID projectId, String title);

    List<SharedStep> findByProjectId(UUID projectId);

    /**
     * {@code query} is matched case-insensitively anywhere in the title; "" lists all. Never null:
     * PostgreSQL cannot type a null parameter in LIKE.
     */
    @Query("SELECT s FROM SharedStep s WHERE s.project.id = :projectId "
            + "AND LOWER(s.title) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<SharedStep> search(@Param("projectId") UUID projectId, @Param("query") String query, Pageable pageable);

    /** Test cases referencing each block, as [sharedStepId, count] rows. */
    @Query("SELECT ts.usesSharedStep.id, COUNT(DISTINCT ts.testCase.id) FROM TestStep ts "
            + "WHERE ts.usesSharedStep.id IN :ids GROUP BY ts.usesSharedStep.id")
    List<Object[]> countUsages(@Param("ids") Collection<UUID> ids);
}
