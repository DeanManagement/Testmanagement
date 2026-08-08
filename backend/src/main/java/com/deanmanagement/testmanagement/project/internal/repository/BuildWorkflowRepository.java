package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.BuildWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BuildWorkflowRepository extends JpaRepository<BuildWorkflow, UUID> {

    List<BuildWorkflow> findByBuildServerConfigIdOrderByName(UUID buildServerConfigId);
}
