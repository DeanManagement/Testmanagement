package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldDefinition;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldEntityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomFieldDefinitionRepository extends JpaRepository<CustomFieldDefinition, UUID> {

    List<CustomFieldDefinition> findByProjectIdOrderByEntityTypeAscOrderIndexAscNameAsc(UUID projectId);

    List<CustomFieldDefinition> findByProjectIdAndEntityTypeOrderByOrderIndexAscNameAsc(
            UUID projectId, CustomFieldEntityType entityType);

    Optional<CustomFieldDefinition> findByIdAndProjectId(UUID id, UUID projectId);

    long countByProjectIdAndEntityTypeAndArchivedFalse(UUID projectId, CustomFieldEntityType entityType);

    @Query("SELECT COALESCE(MAX(d.orderIndex), -1) FROM CustomFieldDefinition d "
            + "WHERE d.project.id = :projectId AND d.entityType = :entityType")
    int maxOrderIndex(@Param("projectId") UUID projectId, @Param("entityType") CustomFieldEntityType entityType);
}
