package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.EntityWatcher;
import com.deanmanagement.testmanagement.project.internal.entity.WatchableEntityType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EntityWatcherRepository extends JpaRepository<EntityWatcher, UUID> {

    List<EntityWatcher> findByUserIdAndEntityType(UUID userId, WatchableEntityType entityType);

    boolean existsByUserIdAndEntityTypeAndEntityId(UUID userId, WatchableEntityType entityType, UUID entityId);

    void deleteByUserIdAndEntityTypeAndEntityId(UUID userId, WatchableEntityType entityType, UUID entityId);

    List<EntityWatcher> findByEntityTypeAndEntityId(WatchableEntityType entityType, UUID entityId);

    List<EntityWatcher> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
