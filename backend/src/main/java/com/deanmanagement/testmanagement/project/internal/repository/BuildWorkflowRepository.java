package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.BuildWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BuildWorkflowRepository extends JpaRepository<BuildWorkflow, UUID> {

    List<BuildWorkflow> findByBuildServerConfigIdOrderByName(UUID buildServerConfigId);

    /** Active workflows on active servers the project may use: what its admin can pick from. */
    @Query("SELECT w FROM BuildWorkflow w JOIN FETCH w.buildServerConfig c "
            + "WHERE w.active = true AND c.active = true "
            + "AND (c.allProjects = true OR :projectId MEMBER OF c.projectIds) ORDER BY w.name")
    List<BuildWorkflow> findAvailableToProject(@Param("projectId") UUID projectId);
}
