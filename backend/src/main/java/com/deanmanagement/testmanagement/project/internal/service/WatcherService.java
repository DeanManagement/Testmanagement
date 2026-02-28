package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.entity.EntityWatcher;
import com.deanmanagement.testmanagement.project.internal.entity.WatchableEntityType;
import com.deanmanagement.testmanagement.project.internal.repository.EntityWatcherRepository;
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

    @Transactional
    public void watch(UUID userId, WatchableEntityType entityType, UUID entityId) {
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
}
