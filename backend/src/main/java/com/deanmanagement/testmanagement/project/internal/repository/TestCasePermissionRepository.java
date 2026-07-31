package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.TestCasePermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestCasePermissionRepository extends JpaRepository<TestCasePermission, UUID> {

    List<TestCasePermission> findByTestCaseIdOrderByCreatedAtAsc(UUID testCaseId);

    List<TestCasePermission> findByUserId(UUID userId);

    Optional<TestCasePermission> findByTestCaseIdAndUserId(UUID testCaseId, UUID userId);
}
