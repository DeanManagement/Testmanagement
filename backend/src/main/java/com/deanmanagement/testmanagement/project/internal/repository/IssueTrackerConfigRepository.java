package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IssueTrackerConfigRepository extends JpaRepository<IssueTrackerConfig, UUID> {

    Optional<IssueTrackerConfig> findByProjectId(UUID projectId);

    boolean existsByActiveTrue();

    void deleteByProjectId(UUID projectId);
}
