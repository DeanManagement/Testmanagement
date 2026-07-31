package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.access.ProjectAccessService;
import com.deanmanagement.testmanagement.project.internal.entity.BugReport;
import com.deanmanagement.testmanagement.project.internal.entity.EntityWatcher;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestPlan;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.entity.WatchableEntityType;
import com.deanmanagement.testmanagement.project.internal.repository.BugReportRepository;
import com.deanmanagement.testmanagement.project.internal.repository.EntityWatcherRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestPlanRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WatcherService {

    private final EntityWatcherRepository entityWatcherRepository;
    private final TestPlanRepository testPlanRepository;
    private final TestRunRepository testRunRepository;
    private final BugReportRepository bugReportRepository;
    private final ProjectAccessService projectAccessService;

    @Transactional
    public void watch(UUID userId, WatchableEntityType entityType, UUID entityId) {
        // PRD-021: watching requires VIEWER access to the entity's project — otherwise any
        // authenticated user could subscribe to arbitrary entity ids and receive notification
        // payloads (names, status changes) from projects they are not a member of.
        UUID projectId = resolveProjectId(entityType, entityId);
        projectAccessService.requireRole(userId, projectId, ProjectRole.VIEWER);

        if (entityWatcherRepository.existsByUserIdAndEntityTypeAndEntityId(userId, entityType, entityId)) {
            return;
        }
        EntityWatcher watcher = new EntityWatcher();
        watcher.setUserId(userId);
        watcher.setEntityType(entityType);
        watcher.setEntityId(entityId);
        watcher.setCreatedAt(Instant.now());
        entityWatcherRepository.save(watcher);
    }

    @Transactional
    public void unwatch(UUID userId, WatchableEntityType entityType, UUID entityId) {
        entityWatcherRepository.deleteByUserIdAndEntityTypeAndEntityId(userId, entityType, entityId);
    }

    public boolean isWatching(UUID userId, WatchableEntityType entityType, UUID entityId) {
        return entityWatcherRepository.existsByUserIdAndEntityTypeAndEntityId(userId, entityType, entityId);
    }

    public List<EntityWatcher> getWatchedEntities(UUID userId) {
        return entityWatcherRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    private UUID resolveProjectId(WatchableEntityType entityType, UUID entityId) {
        return switch (entityType) {
            case TEST_PLAN -> testPlanRepository.findById(entityId)
                    .map(TestPlan::getProject)
                    .orElseThrow(() -> new ResourceNotFoundException("TestPlan", entityId))
                    .getId();
            case TEST_RUN -> testRunRepository.findById(entityId)
                    .map(TestRun::getProject)
                    .orElseThrow(() -> new ResourceNotFoundException("TestRun", entityId))
                    .getId();
            case BUG_REPORT -> bugReportRepository.findById(entityId)
                    .map(BugReport::getProject)
                    .orElseThrow(() -> new ResourceNotFoundException("BugReport", entityId))
                    .getId();
        };
    }
}
