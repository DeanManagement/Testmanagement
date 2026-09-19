package com.deanmanagement.testmanagement.project.internal.buildserver;

import com.deanmanagement.testmanagement.project.internal.entity.BuildWorkflow;
import com.deanmanagement.testmanagement.project.internal.entity.PipelineRun;
import com.deanmanagement.testmanagement.project.internal.entity.PipelineRunStatus;
import com.deanmanagement.testmanagement.project.internal.repository.PipelineRunRepository;
import com.deanmanagement.testmanagement.project.internal.service.BuildServerConfigService;
import com.deanmanagement.testmanagement.project.internal.service.CiIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Pulls the test results a build server collected for a finished run into a test run (PRD-026
 * §3.4), for workflows that ask for it. Results the pipeline pushed itself win: a run that already
 * has a test run is never pulled.
 *
 * <p>HTTP runs outside any transaction: the candidates are read and marked in short transactions of
 * their own, and ingestion opens its own. Every candidate is marked pulled once tried, whatever the
 * outcome, so a pipeline that publishes nothing, or a server that refuses, is asked once and not on
 * every poll; what went wrong is kept on the run.
 */
@Component
public class PipelineResultPuller {

    static final int MAX_RESULTS = 5000;
    private static final int BATCH_SIZE = 10;
    /** Switching pulling on for a workflow must not import its whole history. */
    static final Duration LOOKBACK = Duration.ofDays(1);
    static final Set<PipelineRunStatus> PULLABLE = EnumSet.of(PipelineRunStatus.SUCCESS, PipelineRunStatus.FAILED);

    private static final Logger log = LoggerFactory.getLogger(PipelineResultPuller.class);

    private final PipelineRunRepository runRepository;
    private final BuildServerConfigService configService;
    private final BuildServerProviderRegistry providerRegistry;
    private final CiIngestionService ingestionService;
    private final TransactionTemplate transaction;

    public PipelineResultPuller(PipelineRunRepository runRepository, BuildServerConfigService configService,
                                BuildServerProviderRegistry providerRegistry, CiIngestionService ingestionService,
                                PlatformTransactionManager transactionManager) {
        this.runRepository = runRepository;
        this.configService = configService;
        this.providerRegistry = providerRegistry;
        this.ingestionService = ingestionService;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public boolean hasWork() {
        return runRepository.existsPullable(PULLABLE, cutoff());
    }

    /** @return how many runs got a test run from pulled results */
    public int pullBatch() {
        List<PullJob> jobs = transaction.execute(status -> runRepository
                .findPullable(PULLABLE, cutoff(), PageRequest.of(0, BATCH_SIZE)).stream()
                .map(this::job)
                .toList());
        int linked = 0;
        for (PullJob job : jobs == null ? List.<PullJob>of() : jobs) {
            if (pull(job)) {
                linked++;
            }
        }
        return linked;
    }

    private PullJob job(PipelineRun run) {
        BuildWorkflow workflow = run.getWorkflow();
        var config = workflow.getBuildServerConfig();
        return new PullJob(run.getId(), run.getProjectId(), providerRegistry.require(config.getProvider()),
                configService.decrypt(config),
                new BuildServerProvider.StatusQuery(workflow.getRepoRef(), workflow.getWorkflowRef(),
                        run.getExternalRunId(), run.getTriggeredRef(), run.getId(), run.getCreatedAt()));
    }

    private boolean pull(PullJob job) {
        String note = null;
        boolean linked = false;
        try {
            BuildServerProvider.PulledResults pulled = job.provider().fetchTestResults(job.config(), job.query(),
                    MAX_RESULTS);
            if (!pulled.results().isEmpty()) {
                ingestionService.ingest(job.projectId().toString(), null, null, null, pulled.results(),
                        job.runId(), null);
                linked = true;
            }
            if (pulled.truncated()) {
                note = "Only the first " + MAX_RESULTS + " test results were pulled";
            }
        } catch (UnsupportedOperationException e) {
            note = e.getMessage();
        } catch (RuntimeException e) {
            // Token, network or a bad run reference: said on the run, not retried every poll.
            note = "Pulling test results failed: " + e.getMessage();
            log.warn("Could not pull test results for pipeline run {}: {}", job.runId(), e.getMessage());
        }
        String message = note;
        transaction.executeWithoutResult(status -> markPulled(job.runId(), message));
        return linked;
    }

    private void markPulled(UUID runId, String note) {
        runRepository.findById(runId).ifPresent(run -> {
            run.setResultsPulledAt(Instant.now());
            if (note != null) {
                run.setErrorMessage(run.getErrorMessage() == null ? note : run.getErrorMessage() + "\n" + note);
            }
            runRepository.save(run);
        });
    }

    private static Instant cutoff() {
        return Instant.now().minus(LOOKBACK);
    }

    private record PullJob(UUID runId, UUID projectId, BuildServerProvider provider,
                           BuildServerProvider.DecryptedConfig config, BuildServerProvider.StatusQuery query) {
    }
}
