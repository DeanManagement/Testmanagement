package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.ProjectEnvironment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Environments, plus the bulk writes that keep {@code test_runs.environment} and
 * {@code bug_reports.environment} (denormalised names) in step with them. Keeping those here means
 * every write to the copies lives next to the catalogue (PRD-032 §3.1).
 */
public interface ProjectEnvironmentRepository extends JpaRepository<ProjectEnvironment, UUID> {

    List<ProjectEnvironment> findByProjectIdOrderBySortOrderAscNameAsc(UUID projectId);

    Optional<ProjectEnvironment> findByIdAndProjectId(UUID id, UUID projectId);

    Optional<ProjectEnvironment> findByProjectIdAndNameNormalized(UUID projectId, String nameNormalized);

    @Query("SELECT COALESCE(MAX(e.sortOrder), -1) FROM ProjectEnvironment e WHERE e.project.id = :projectId")
    int maxSortOrder(@Param("projectId") UUID projectId);

    @Query("SELECT r.projectEnvironment.id, COUNT(r) FROM TestRun r "
            + "WHERE r.project.id = :projectId AND r.projectEnvironment IS NOT NULL GROUP BY r.projectEnvironment.id")
    List<Object[]> countRunsByEnvironment(@Param("projectId") UUID projectId);

    @Query("SELECT b.projectEnvironment.id, COUNT(b) FROM BugReport b "
            + "WHERE b.project.id = :projectId AND b.projectEnvironment IS NOT NULL GROUP BY b.projectEnvironment.id")
    List<Object[]> countBugsByEnvironment(@Param("projectId") UUID projectId);

    @Query("SELECT COUNT(r) FROM TestRun r WHERE r.projectEnvironment.id = :id")
    long countRuns(@Param("id") UUID id);

    @Query("SELECT COUNT(b) FROM BugReport b WHERE b.projectEnvironment.id = :id")
    long countBugs(@Param("id") UUID id);

    /** PRD-034: sessions keep only the reference, not a name copy. */
    @Query("SELECT COUNT(s) FROM ExploratorySession s WHERE s.environment.id = :id")
    long countSessions(@Param("id") UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ExploratorySession s SET s.environment = :target WHERE s.environment = :source")
    int repointSessions(@Param("source") ProjectEnvironment source, @Param("target") ProjectEnvironment target);

    /** Points runs at {@code target} (which may equal {@code source}) and rewrites their name copy. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE TestRun r SET r.projectEnvironment = :target, r.environment = :name "
            + "WHERE r.projectEnvironment = :source")
    int repointRuns(@Param("source") ProjectEnvironment source, @Param("target") ProjectEnvironment target,
                    @Param("name") String name);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE BugReport b SET b.projectEnvironment = :target, b.environment = :name "
            + "WHERE b.projectEnvironment = :source")
    int repointBugs(@Param("source") ProjectEnvironment source, @Param("target") ProjectEnvironment target,
                    @Param("name") String name);
}
