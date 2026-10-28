package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.SavedFilter;
import com.deanmanagement.testmanagement.project.internal.entity.SavedFilterView;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavedFilterRepository extends JpaRepository<SavedFilter, UUID> {

    /** The caller's own filters and everyone's shared ones, for one list of one project. */
    @Query("""
           SELECT f FROM SavedFilter f
           WHERE f.projectId = :projectId AND f.view = :view AND (f.ownerId = :userId OR f.shared = true)
           ORDER BY LOWER(f.name)
           """)
    List<SavedFilter> findVisible(@Param("projectId") UUID projectId, @Param("view") SavedFilterView view,
                                  @Param("userId") UUID userId);

    Optional<SavedFilter> findByIdAndProjectId(UUID id, UUID projectId);
}
