package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.Project;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    boolean existsByKey(String key);

    Optional<Project> findByKey(String key);

    List<Project> findByKeyContainingIgnoreCase(String key);

    @Query("SELECT p FROM Project p JOIN p.members m WHERE m.user.id = :userId")
    List<Project> findByMembersUserId(@Param("userId") UUID userId);

    /**
     * Pessimistically locks the project row so the per-project test-case/run number can be read and
     * incremented atomically. Portable across PostgreSQL and the H2 dev/test DB (replaces a
     * Postgres-only {@code UPDATE ... RETURNING}).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Project p WHERE p.id = :projectId")
    Optional<Project> findByIdForUpdate(@Param("projectId") UUID projectId);
}
