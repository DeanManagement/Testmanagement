package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestCaseRepository extends JpaRepository<TestCase, UUID> {

    Optional<TestCase> findByKeyAndProjectId(String key, UUID projectId);

    List<TestCase> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    long countByProjectId(UUID projectId);

    @Query("SELECT CAST(tc.status AS string), COUNT(tc) FROM TestCase tc WHERE tc.project.id = :projectId GROUP BY tc.status")
    List<Object[]> countByProjectIdGroupByStatus(@Param("projectId") UUID projectId);

    @Query("SELECT CAST(tc.priority AS string), COUNT(tc) FROM TestCase tc WHERE tc.project.id = :projectId GROUP BY tc.priority")
    List<Object[]> countByProjectIdGroupByPriority(@Param("projectId") UUID projectId);

    List<TestCase> findByProjectIdAndFolderIdOrderByCreatedAtDesc(UUID projectId, UUID folderId);

    List<TestCase> findByProjectIdAndFolderIsNullOrderByCreatedAtDesc(UUID projectId);

    long countByFolderId(UUID folderId);

    @Query("SELECT DISTINCT tc FROM TestCase tc LEFT JOIN FETCH tc.steps s LEFT JOIN FETCH s.image WHERE tc.project.id = :projectId ORDER BY tc.createdAt DESC")
    List<TestCase> findByProjectIdWithSteps(@Param("projectId") UUID projectId);

    @Query("SELECT DISTINCT tc FROM TestCase tc LEFT JOIN FETCH tc.steps s LEFT JOIN FETCH s.image WHERE tc.project.id = :projectId AND tc.folder.id = :folderId ORDER BY tc.createdAt DESC")
    List<TestCase> findByProjectIdAndFolderIdWithSteps(@Param("projectId") UUID projectId, @Param("folderId") UUID folderId);

    @Query("SELECT DISTINCT tc FROM TestCase tc LEFT JOIN FETCH tc.steps s LEFT JOIN FETCH s.image WHERE tc.project.id = :projectId AND tc.folder IS NULL ORDER BY tc.createdAt DESC")
    List<TestCase> findByProjectIdAndFolderIsNullWithSteps(@Param("projectId") UUID projectId);

    @Query("SELECT tc FROM TestCase tc LEFT JOIN FETCH tc.steps s LEFT JOIN FETCH s.image WHERE tc.id = :id")
    Optional<TestCase> findByIdWithSteps(@Param("id") UUID id);
}
