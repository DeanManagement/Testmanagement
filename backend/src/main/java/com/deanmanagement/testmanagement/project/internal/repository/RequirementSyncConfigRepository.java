package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.RequirementSyncConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RequirementSyncConfigRepository extends JpaRepository<RequirementSyncConfig, UUID> {

    Optional<RequirementSyncConfig> findByProjectId(UUID projectId);

    List<RequirementSyncConfig> findByEnabledTrueAndScheduledTrue();
}
