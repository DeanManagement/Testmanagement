package com.deanmanagement.testmanagement.project.internal.buildserver;

import com.deanmanagement.testmanagement.project.internal.repository.BuildServerConfigRepository;
import com.deanmanagement.testmanagement.project.internal.repository.PipelineRunRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives the periodic status refresh of triggered pipelines (PRD-024 §3.3).
 *
 * <p>The interval is short — a tester is watching — but two guards keep it quiet: it returns
 * immediately unless a build server is registered and active (air-gap safe), and again unless
 * some run is actually non-terminal. An idle instance therefore makes no outbound calls at all,
 * and both guards are local DB lookups.
 */
@Component
@RequiredArgsConstructor
public class PipelineStatusPoller {

    private static final Logger log = LoggerFactory.getLogger(PipelineStatusPoller.class);

    private final BuildServerConfigRepository configRepository;
    private final PipelineRunRepository runRepository;
    private final PipelineRunRefresher refresher;

    @Scheduled(fixedDelayString = "${app.buildserver.poll-interval-ms:15000}")
    public void poll() {
        if (!configRepository.existsByActiveTrue()) {
            return;
        }
        if (!runRepository.existsByStatusIn(PipelineRunRefresher.NON_TERMINAL)) {
            return;
        }
        try {
            refresher.refreshBatch();
        } catch (Exception e) {
            log.warn("Pipeline status poll pass failed: {}", e.getMessage());
        }
    }
}
