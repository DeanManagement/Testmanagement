package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.BugStatusTransition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BugStatusTransitionRepository extends JpaRepository<BugStatusTransition, UUID> {

    List<BugStatusTransition> findByProjectId(UUID projectId);

    boolean existsByProjectId(UUID projectId);

    boolean existsByFromStatusIdAndToStatusId(UUID fromStatusId, UUID toStatusId);

    void deleteByProjectId(UUID projectId);
}
