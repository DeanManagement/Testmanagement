package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.entity.PipelineRun;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.repository.PipelineRunRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Connects externally reported results to the pipeline run that produced them (PRD-024 §3.3).
 * A triggered workflow receives its {@code TM_PIPELINE_RUN_ID} and passes it back as the
 * {@code pipelineRunId} request parameter on the PRD-005 ingestion endpoints.
 *
 * <p>Runs inside the caller's ingestion transaction. The pipeline run must belong to the API
 * key's project — reported as not-found rather than forbidden, since to that caller a pipeline
 * run in another project may as well not exist (PRD-021 discipline).
 */
@Component
@RequiredArgsConstructor
public class PipelineRunLinker {

    private static final Logger log = LoggerFactory.getLogger(PipelineRunLinker.class);

    private final PipelineRunRepository pipelineRunRepository;

    /** Resolves and validates the pipeline run, or null when no id was submitted. */
    public PipelineRun resolve(UUID pipelineRunId, UUID projectId) {
        if (pipelineRunId == null) {
            return null;
        }
        return pipelineRunRepository.findByIdAndProjectId(pipelineRunId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("PipelineRun", pipelineRunId));
    }

    /**
     * Links the created test run. First report wins: a workflow that posts twice creates two test
     * runs, but the pipeline run keeps pointing at the first — logged, not an error, so a retried
     * CI step cannot fail the upload.
     */
    public void attach(PipelineRun pipelineRun, TestRun testRun) {
        if (pipelineRun == null) {
            return;
        }
        if (pipelineRun.getTestRun() != null) {
            log.info("Pipeline run {} already has test run {}; keeping the first link",
                    pipelineRun.getId(), pipelineRun.getTestRun().getKey());
            return;
        }
        pipelineRun.setTestRun(testRun);
        pipelineRunRepository.save(pipelineRun);
    }
}
