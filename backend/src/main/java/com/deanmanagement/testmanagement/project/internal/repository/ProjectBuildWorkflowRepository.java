package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.ProjectBuildWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectBuildWorkflowRepository extends JpaRepository<ProjectBuildWorkflow, UUID> {

    /** Assignments for a project with workflow and server loaded, ready for DTO mapping. */
    @Query("SELECT a FROM ProjectBuildWorkflow a JOIN FETCH a.workflow w "
            + "JOIN FETCH w.buildServerConfig WHERE a.projectId = :projectId ORDER BY w.name")
    List<ProjectBuildWorkflow> findByProjectIdWithWorkflow(@Param("projectId") UUID projectId);

    Optional<ProjectBuildWorkflow> findByProjectIdAndWorkflowId(UUID projectId, UUID workflowId);

    /**
     * The trigger path's lookup: workflow and server fetched along, because the trigger service
     * deliberately runs outside a transaction (no HTTP inside a DB transaction) and a lazy proxy
     * would fail there with no session to initialize from.
     */
    @Query("SELECT a FROM ProjectBuildWorkflow a JOIN FETCH a.workflow w "
            + "JOIN FETCH w.buildServerConfig WHERE a.projectId = :projectId AND w.id = :workflowId")
    Optional<ProjectBuildWorkflow> findForTrigger(@Param("projectId") UUID projectId,
                                                  @Param("workflowId") UUID workflowId);

    List<ProjectBuildWorkflow> findByWorkflowId(UUID workflowId);

    void deleteByWorkflowIdAndProjectIdNotIn(UUID workflowId, List<UUID> projectIds);

    void deleteByWorkflowId(UUID workflowId);
}
