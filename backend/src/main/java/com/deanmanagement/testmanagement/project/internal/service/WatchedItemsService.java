package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.watcher.WatchedItemResponse;
import com.deanmanagement.testmanagement.project.internal.entity.BugReport;
import com.deanmanagement.testmanagement.project.internal.entity.EntityWatcher;
import com.deanmanagement.testmanagement.project.internal.entity.TestPlan;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.entity.WatchableEntityType;
import com.deanmanagement.testmanagement.project.internal.repository.BugReportRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestPlanRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WatchedItemsService {

    private final WatcherService watcherService;
    private final TestPlanRepository testPlanRepository;
    private final TestRunRepository testRunRepository;
    private final BugReportRepository bugReportRepository;

    public List<WatchedItemResponse> getWatchedItems(UUID userId) {
        List<EntityWatcher> watchers = watcherService.getWatchedEntities(userId);
        List<WatchedItemResponse> items = new ArrayList<>();

        for (EntityWatcher watcher : watchers) {
            WatchedItemResponse item = resolveItem(watcher);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    private WatchedItemResponse resolveItem(EntityWatcher watcher) {
        return switch (watcher.getEntityType()) {
            case TEST_PLAN -> testPlanRepository.findById(watcher.getEntityId())
                    .map(plan -> toResponse(watcher, plan))
                    .orElse(null);
            case TEST_RUN -> testRunRepository.findById(watcher.getEntityId())
                    .map(run -> toResponse(watcher, run))
                    .orElse(null);
            case BUG_REPORT -> bugReportRepository.findById(watcher.getEntityId())
                    .map(bug -> toResponse(watcher, bug))
                    .orElse(null);
        };
    }

    private WatchedItemResponse toResponse(EntityWatcher watcher, TestPlan plan) {
        return new WatchedItemResponse(
                WatchableEntityType.TEST_PLAN,
                plan.getId(),
                plan.getName(),
                plan.getStatus().name(),
                plan.getProject().getId(),
                watcher.getCreatedAt()
        );
    }

    private WatchedItemResponse toResponse(EntityWatcher watcher, TestRun run) {
        return new WatchedItemResponse(
                WatchableEntityType.TEST_RUN,
                run.getId(),
                run.getName(),
                run.getStatus().name(),
                run.getProject().getId(),
                watcher.getCreatedAt()
        );
    }

    private WatchedItemResponse toResponse(EntityWatcher watcher, BugReport bug) {
        return new WatchedItemResponse(
                WatchableEntityType.BUG_REPORT,
                bug.getId(),
                bug.getTitle(),
                bug.getStatus().name(),
                bug.getProject().getId(),
                watcher.getCreatedAt()
        );
    }
}
