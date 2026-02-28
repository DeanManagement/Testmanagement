package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.TestCaseFolder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TestCaseFolderRepository extends JpaRepository<TestCaseFolder, UUID> {

    List<TestCaseFolder> findByProjectIdOrderBySortOrderAsc(UUID projectId);

    List<TestCaseFolder> findByProjectIdAndParentIsNullOrderBySortOrderAsc(UUID projectId);

    long countByProjectId(UUID projectId);
}
