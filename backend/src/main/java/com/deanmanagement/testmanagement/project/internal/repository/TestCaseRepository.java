package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestCaseRepository extends JpaRepository<TestCase, UUID>, JpaSpecificationExecutor<TestCase> {

    Optional<TestCase> findByKeyAndProjectId(String key, UUID projectId);

    Optional<TestCase> findFirstByProjectIdAndTitle(UUID projectId, String title);

    /**
     * Project-scoped batch lookup. Callers that resolve caller-supplied ids must use this rather
     * than {@code findAllById}, which would happily return another project's cases.
     */
    List<TestCase> findByIdInAndProjectId(Collection<UUID> ids, UUID projectId);

    /** Single-id counterpart of the above, for the same reason (PRD-027 §3.5). */
    Optional<TestCase> findByIdAndProjectId(UUID id, UUID projectId);

    List<TestCase> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    long countByProjectId(UUID projectId);

    /**
     * Id/key/title only, for the MCP duplicate guard (PRD-025 §3.5). Loading whole entities to
     * compare titles would pull every step of every case with them.
     */
    @Query("SELECT tc.id, tc.key, tc.title FROM TestCase tc WHERE tc.project.id = :projectId")
    List<Object[]> findTitlesByProjectId(@Param("projectId") UUID projectId);

    /** Every label used in the project, once each, for the label filter (PRD-052). */
    @Query("SELECT DISTINCT l FROM TestCase tc JOIN tc.labels l WHERE tc.project.id = :projectId ORDER BY l")
    List<String> findDistinctLabelsByProjectId(@Param("projectId") UUID projectId);

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

    /**
     * Test cases authored by a user that are still in a given status and have
     * not been touched since the supplied cutoff. Used by the "My queue"
     * widget to nudge authors towards finishing long-lived DRAFTs.
     */
    @Query("SELECT tc FROM TestCase tc " +
            "JOIN FETCH tc.project " +
            "WHERE tc.createdBy = :userId " +
            "AND tc.status = :status " +
            "AND tc.updatedAt < :staleBefore " +
            "ORDER BY tc.updatedAt ASC")
    List<TestCase> findStaleByCreatedByAndStatus(@Param("userId") UUID userId,
                                                 @Param("status") TestCaseStatus status,
                                                 @Param("staleBefore") Instant staleBefore,
                                                 Pageable pageable);

    /**
     * Cases waiting for a review the user may give (PRD-033): IN_REVIEW, in a project where the
     * user's membership meets the reviewer role (ADMIN always; TESTER when the project lets
     * testers review, or doesn't require review at all), and neither written nor last edited by
     * the user. Oldest first, since they've waited longest.
     */
    @Query("SELECT tc FROM TestCase tc JOIN FETCH tc.project p, ProjectMember m " +
            "WHERE m.project = p AND m.user.id = :userId AND tc.status = :inReview " +
            "AND (tc.createdBy IS NULL OR tc.createdBy <> :userId) " +
            "AND (tc.updatedBy IS NULL OR tc.updatedBy <> :userId) " +
            "AND (m.role = :admin OR (m.role = :tester " +
            "     AND (p.reviewRequired = false OR p.reviewerMinRole = :tester))) " +
            "ORDER BY tc.updatedAt ASC")
    List<TestCase> findAwaitingReviewBy(@Param("userId") UUID userId,
                                        @Param("inReview") TestCaseStatus inReview,
                                        @Param("admin") ProjectRole admin,
                                        @Param("tester") ProjectRole tester,
                                        Pageable pageable);

    /** Test cases with a step referencing the block (PRD-030), in key order of creation. */
    @Query("SELECT DISTINCT tc FROM TestCase tc JOIN tc.steps s WHERE s.usesSharedStep.id = :sharedStepId "
            + "ORDER BY tc.createdAt")
    List<TestCase> findUsingSharedStep(@Param("sharedStepId") UUID sharedStepId);
}
