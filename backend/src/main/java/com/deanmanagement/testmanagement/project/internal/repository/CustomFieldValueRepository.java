package com.deanmanagement.testmanagement.project.internal.repository;

import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CustomFieldValueRepository extends JpaRepository<CustomFieldValue, UUID> {

    long countByFieldId(UUID fieldId);

    @Query("SELECT DISTINCT v.valueText FROM CustomFieldValue v WHERE v.field.id = :fieldId AND v.valueText IN :options")
    List<String> findUsedOptions(@Param("fieldId") UUID fieldId, @Param("options") Collection<String> options);

    /** Rewrites stored option labels when an option is renamed (PRD-035 §3.2). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE CustomFieldValue v SET v.valueText = :newText WHERE v.field.id = :fieldId AND v.valueText = :oldText")
    int renameOption(@Param("fieldId") UUID fieldId, @Param("oldText") String oldText, @Param("newText") String newText);
}
