package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.TestCaseLink;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseLinkType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestCaseLinkRepository extends JpaRepository<TestCaseLink, UUID> {

    /** Every link the case takes part in, on either side, with both cases fetched. */
    @Query("SELECT l FROM TestCaseLink l JOIN FETCH l.source JOIN FETCH l.target "
            + "WHERE l.source.id = :caseId OR l.target.id = :caseId")
    List<TestCaseLink> findInvolving(@Param("caseId") UUID caseId);

    Optional<TestCaseLink> findBySourceIdAndTargetIdAndLinkType(UUID sourceId, UUID targetId,
                                                                TestCaseLinkType linkType);
}
