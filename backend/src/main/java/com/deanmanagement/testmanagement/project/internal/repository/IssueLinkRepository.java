package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.IssueLink;
import com.deanmanagement.testmanagement.project.internal.entity.IssueTrackerProviderType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IssueLinkRepository extends JpaRepository<IssueLink, UUID> {

    List<IssueLink> findByTestResultIdOrderByCreatedAtAsc(UUID testResultId);

    List<IssueLink> findByTestResultIdInOrderByCreatedAtAsc(Collection<UUID> testResultIds);

    Optional<IssueLink> findByTestResultIdAndExternalId(UUID testResultId, String externalId);

    /** TM50: the work item a bug report was handed over to. */
    Optional<IssueLink> findByBugReportId(UUID bugReportId);

    /** TM50: another bug already following this work item. */
    Optional<IssueLink> findFirstByProviderAndExternalIdAndBugReportIdIsNotNull(IssueTrackerProviderType provider,
                                                                              String externalId);

    /**
     * TM50: a project's bug links worth polling, oldest-checked first. Closed bugs too: a work
     * item reopened in the tracker reopens its bug.
     */
    @Query("""
            SELECT l FROM IssueLink l, BugReport b
            WHERE b.id = l.bugReportId AND b.project.id = :projectId
            AND (l.stateCheckedAt IS NULL OR l.stateCheckedAt < :staleBefore)
            ORDER BY l.stateCheckedAt ASC NULLS FIRST
            """)
    List<IssueLink> findStaleForBugReports(@Param("projectId") UUID projectId,
                                           @Param("staleBefore") Instant staleBefore,
                                           Pageable pageable);

    /**
     * Links worth polling: only those on runs that are still planned or in progress, since a
     * completed run's defect state is history. Ordered oldest-checked-first so a bounded batch
     * still gives every link a turn.
     */
    @Query("""
            SELECT l FROM IssueLink l
            WHERE l.testResultId IN (
                SELECT r.id FROM TestResult r
                WHERE r.testRun.project.id = :projectId
                  AND r.testRun.status IN ('PLANNED', 'IN_PROGRESS')
            )
            AND (l.stateCheckedAt IS NULL OR l.stateCheckedAt < :staleBefore)
            ORDER BY l.stateCheckedAt ASC NULLS FIRST
            """)
    List<IssueLink> findStaleForActiveRuns(@Param("projectId") UUID projectId,
                                           @Param("staleBefore") Instant staleBefore,
                                           Pageable pageable);
}
