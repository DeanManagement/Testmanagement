package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.TestCaseParameterSet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TestCaseParameterSetRepository extends JpaRepository<TestCaseParameterSet, UUID> {

    List<TestCaseParameterSet> findByTestCaseIdOrderByOrderIndexAsc(UUID testCaseId);

    List<TestCaseParameterSet> findByTestCaseIdInOrderByOrderIndexAsc(Collection<UUID> testCaseIds);

    Optional<TestCaseParameterSet> findByIdAndTestCaseId(UUID id, UUID testCaseId);

    boolean existsByTestCaseIdAndName(UUID testCaseId, String name);

    long countByTestCaseId(UUID testCaseId);
}
