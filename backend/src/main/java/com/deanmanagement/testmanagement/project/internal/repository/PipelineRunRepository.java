package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.PipelineRun;
import com.deanmanagement.testmanagement.project.internal.entity.PipelineRunStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PipelineRunRepository extends JpaRepository<PipelineRun, UUID> {

    Page<PipelineRun> findByProjectIdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);

    Optional<PipelineRun> findByIdAndProjectId(UUID id, UUID projectId);

    boolean existsByStatusIn(Collection<PipelineRunStatus> statuses);

    /**
     * Non-terminal runs for the poller, least-recently-polled first so a backlog drains fairly.
     * Workflow and server are fetched along, since the poller needs both to place the call.
     */
    @Query("SELECT r FROM PipelineRun r LEFT JOIN FETCH r.workflow w "
            + "LEFT JOIN FETCH w.buildServerConfig WHERE r.status IN :statuses "
            + "ORDER BY r.lastPolledAt ASC NULLS FIRST")
    List<PipelineRun> findPollable(@Param("statuses") Collection<PipelineRunStatus> statuses,
                                   Pageable pageable);
}
