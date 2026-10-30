package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.BugReportRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface BugReportRelationRepository extends JpaRepository<BugReportRelation, UUID> {

    /** Both directions: where the bug is the source or the target. */
    @Query("SELECT r FROM BugReportRelation r JOIN FETCH r.bugReport JOIN FETCH r.relatedBug "
            + "WHERE r.bugReport.id = :bugId OR r.relatedBug.id = :bugId ORDER BY r.createdAt")
    List<BugReportRelation> findInvolving(@Param("bugId") UUID bugId);
}
