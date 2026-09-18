package com.deanmanagement.testmanagement.project.internal.repository;

import java.util.Set;
import java.util.Collection;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestCaseVersionRepository extends JpaRepository<TestCaseVersion, UUID> {

    List<TestCaseVersion> findByTestCaseIdOrderByVersionNumberDesc(UUID testCaseId);

    Optional<TestCaseVersion> findByTestCaseIdAndVersionNumber(UUID testCaseId, int versionNumber);

    /** "caseId:version" of every superseded version that was approved (PRD-033). */
    @Query("SELECT CONCAT(CAST(v.testCaseId AS string), ':', CAST(v.versionNumber AS string)) FROM TestCaseVersion v "
            + "WHERE v.testCaseId IN :testCaseIds AND v.approvedVersion = v.versionNumber")
    Set<String> findApprovedVersionKeys(@Param("testCaseIds") Collection<UUID> testCaseIds);
}
