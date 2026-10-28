package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.ProjectResultStatus;
import com.deanmanagement.testmanagement.project.internal.entity.TestResultStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectResultStatusRepository extends JpaRepository<ProjectResultStatus, UUID> {

    List<ProjectResultStatus> findByProjectIdOrderByPositionAscNameAsc(UUID projectId);

    Optional<ProjectResultStatus> findByProjectIdAndNameNormalized(UUID projectId, String nameNormalized);

    long countByProjectIdAndCategoryAndArchivedFalse(UUID projectId, TestResultStatus category);
}
