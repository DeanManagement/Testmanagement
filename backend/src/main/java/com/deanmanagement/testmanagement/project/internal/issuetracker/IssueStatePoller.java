package com.deanmanagement.testmanagement.project.internal.issuetracker;

import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerConfig;
import com.deanmanagement.testmanagement.project.internal.repository.IssueTrackerConfigRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drives the periodic refresh of linked-issue state (PRD-010 §3.3).
 *
 * <p>Three things keep this from becoming a source of API abuse. It returns immediately unless some
 * project has an active config, so an air-gapped install makes no outbound calls at all. Only links
 * on runs that are still planned or in progress are considered, since a finished run's defect state
 * is history. And each pass is capped at a batch size, oldest-checked first, so a large backlog
 * drains over several cycles instead of bursting.
 */
@Component
@RequiredArgsConstructor
public class IssueStatePoller {

    private static final Logger log = LoggerFactory.getLogger(IssueStatePoller.class);

    private final IssueTrackerConfigRepository configRepository;
    private final IssueStateRefresher refresher;

    @Scheduled(fixedDelayString = "${app.issuetracker.poll-interval-ms:300000}")
    public void poll() {
        if (!configRepository.existsByActiveTrue()) {
            return;
        }
        for (IssueTrackerConfig config : configRepository.findAll()) {
            if (!config.isActive()) {
                continue;
            }
            try {
                refresher.refreshProject(config);
            } catch (Exception e) {
                // One project's misconfiguration must not stop the others being polled.
                log.warn("Issue state poll failed for project {}: {}", config.getProjectId(), e.getMessage());
            }
        }
    }
}
