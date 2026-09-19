package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.BugReportLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BugReportLinkRepository extends JpaRepository<BugReportLink, UUID> {

    Optional<BugReportLink> findByBugReportIdAndTestResultId(UUID bugReportId, UUID testResultId);

    Optional<BugReportLink> findByIdAndBugReportId(UUID id, UUID bugReportId);
}
