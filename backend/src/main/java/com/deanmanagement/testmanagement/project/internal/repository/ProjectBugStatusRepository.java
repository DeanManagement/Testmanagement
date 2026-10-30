package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.BugReportStatus;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectBugStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectBugStatusRepository extends JpaRepository<ProjectBugStatus, UUID> {

    List<ProjectBugStatus> findByProjectIdOrderByPositionAscNameAsc(UUID projectId);

    Optional<ProjectBugStatus> findByProjectIdAndNameNormalized(UUID projectId, String nameNormalized);

    long countByProjectIdAndCategoryAndArchivedFalse(UUID projectId, BugReportStatus category);

    Optional<ProjectBugStatus> findFirstByProjectIdAndCategoryAndArchivedFalseOrderByPositionAscNameAsc(
            UUID projectId, BugReportStatus category);
}
