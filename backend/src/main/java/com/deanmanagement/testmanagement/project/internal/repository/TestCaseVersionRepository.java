package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.TestCaseVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestCaseVersionRepository extends JpaRepository<TestCaseVersion, UUID> {

    List<TestCaseVersion> findByTestCaseIdOrderByVersionNumberDesc(UUID testCaseId);

    Optional<TestCaseVersion> findByTestCaseIdAndVersionNumber(UUID testCaseId, int versionNumber);
}
