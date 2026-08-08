package com.deanmanagement.testmanagement.project.internal.buildserver;

import com.deanmanagement.testmanagement.project.internal.entity.BuildServerConfig;
import com.deanmanagement.testmanagement.project.internal.entity.BuildWorkflow;
import com.deanmanagement.testmanagement.project.internal.entity.PipelineRun;
import com.deanmanagement.testmanagement.project.internal.entity.PipelineRunStatus;
import com.deanmanagement.testmanagement.project.internal.repository.PipelineRunRepository;
import com.deanmanagement.testmanagement.project.internal.service.BuildServerConfigService;
import com.deanmanagement.testmanagement.shared.exception.UpstreamServiceException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Advances non-terminal pipeline runs by asking their build server (PRD-024 §3.3).
 *
 * <p>Separate from {@link PipelineStatusPoller} on purpose: {@code @Transactional} is applied by
 * a proxy, so a scheduled method calling a transactional method on {@code this} would silently
 * run without a transaction. Crossing a bean boundary is what makes the annotation take effect.
 */
@Component
@RequiredArgsConstructor
public class PipelineRunRefresher {

    static final Set<PipelineRunStatus> NON_TERMINAL =
            EnumSet.of(PipelineRunStatus.TRIGGERED, PipelineRunStatus.PENDING, PipelineRunStatus.RUNNING);

    private static final Logger log = LoggerFactory.getLogger(PipelineRunRefresher.class);

    private final PipelineRunRepository runRepository;
    private final BuildServerConfigService configService;
    private final BuildServerProviderRegistry providerRegistry;
    private final BuildServerProperties properties;

    /** @return number of runs advanced to a new status. */
    @Transactional
    public int refreshBatch() {
        List<PipelineRun> runs = runRepository.findPollable(NON_TERMINAL,
                PageRequest.of(0, properties.pollBatchSize()));
        // A server whose call failed this pass is skipped for its remaining runs, so an auth
        // failure is reported once instead of once per run.
        Set<UUID> failedConfigs = new HashSet<>();
        int advanced = 0;
        for (PipelineRun run : runs) {
            if (refresh(run, failedConfigs)) {
                advanced++;
            }
        }
        return advanced;
    }

    /** On-demand refresh of a single run, for the tester's refresh button. */
    @Transactional
    public void refreshOne(UUID runId) {
        runRepository.findById(runId)
                .filter(run -> !run.getStatus().isTerminal())
                .ifPresent(run -> refresh(run, new HashSet<>()));
    }

    private boolean refresh(PipelineRun run, Set<UUID> failedConfigs) {
        PipelineRunStatus before = run.getStatus();
        run.setLastPolledAt(Instant.now());

        if (expired(run)) {
            finish(run, PipelineRunStatus.TIMED_OUT,
                    "No terminal status within " + properties.runTimeoutMinutes() + " minutes");
            runRepository.save(run);
            return true;
        }

        BuildWorkflow workflow = run.getWorkflow();
        if (workflow == null) {
            // The workflow (or its server) was deleted mid-flight; the run can no longer be polled.
            finish(run, PipelineRunStatus.ERROR, "The workflow definition was removed while the run was in flight");
            runRepository.save(run);
            return true;
        }
        BuildServerConfig config = workflow.getBuildServerConfig();
        if (!config.isActive() || failedConfigs.contains(config.getId())) {
            runRepository.save(run);
            return false;
        }

        try {
            BuildServerProvider provider = providerRegistry.require(config.getProvider());
            BuildServerProvider.StatusResult result = provider.fetchStatus(
                    configService.decrypt(config),
                    new BuildServerProvider.StatusQuery(workflow.getRepoRef(), workflow.getWorkflowRef(),
                            run.getExternalRunId(), run.getTriggeredRef(), run.getId(),
                            run.getCreatedAt()));
            apply(run, result);
        } catch (UpstreamServiceException e) {
            failedConfigs.add(config.getId());
            configService.recordError(config, e.getMessage());
            log.warn("Pipeline status poll failed on build server '{}': {}", config.getName(), e.getMessage());
        } catch (RuntimeException e) {
            // A single unresolvable run (bad reference) should not stop the batch; stamping
            // lastPolledAt stops it monopolising the oldest-first ordering.
            log.warn("Could not refresh pipeline run {}: {}", run.getId(), e.getMessage());
        }
        runRepository.save(run);
        return run.getStatus() != before;
    }

    private void apply(PipelineRun run, BuildServerProvider.StatusResult result) {
        if (result.externalRunId() != null) {
            run.setExternalRunId(result.externalRunId());
        }
        if (result.externalUrl() != null) {
            run.setExternalUrl(result.externalUrl());
        }
        if (result.status() == null) {
            return; // No verdict this pass (dispatch not visible yet); keep the stored status.
        }
        run.setStatus(result.status());
        if (result.status().isTerminal() && run.getFinishedAt() == null) {
            run.setFinishedAt(Instant.now());
        }
    }

    private boolean expired(PipelineRun run) {
        return run.getCreatedAt() != null && run.getCreatedAt()
                .plus(Duration.ofMinutes(properties.runTimeoutMinutes()))
                .isBefore(Instant.now());
    }

    private static void finish(PipelineRun run, PipelineRunStatus status, String message) {
        run.setStatus(status);
        run.setErrorMessage(message);
        run.setFinishedAt(Instant.now());
    }
}
