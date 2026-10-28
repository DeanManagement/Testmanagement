package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.entity.RequirementSyncConfig;
import com.deanmanagement.testmanagement.project.internal.repository.RequirementSyncConfigRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled requirement sync (TM19). Off unless {@code app.requirementsync.cron} is set; then every
 * project with sync enabled and "scheduled" ticked is synced in turn. One project failing is logged
 * and does not stop the others.
 */
@Component
@RequiredArgsConstructor
public class RequirementSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(RequirementSyncScheduler.class);

    private final RequirementSyncConfigRepository syncConfigRepository;
    private final RequirementSyncService syncService;

    @Scheduled(cron = "${app.requirementsync.cron:-}")
    public void syncAll() {
        for (RequirementSyncConfig config : syncConfigRepository.findByEnabledTrueAndScheduledTrue()) {
            try {
                var result = syncService.sync(config.getProjectId(), false, null);
                log.info("Scheduled requirement sync for project {}: {} created, {} updated, {} obsoleted",
                        config.getProjectId(), result.created(), result.updated(), result.obsoleted());
            } catch (RuntimeException e) {
                log.warn("Scheduled requirement sync failed for project {}: {}", config.getProjectId(),
                        e.getMessage(), e);
            }
        }
    }
}
