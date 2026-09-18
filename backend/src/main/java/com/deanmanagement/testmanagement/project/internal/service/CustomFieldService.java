package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.customField.CreateCustomFieldRequest;
import com.deanmanagement.testmanagement.project.internal.dto.customField.CustomFieldResponse;
import com.deanmanagement.testmanagement.project.internal.dto.customField.UpdateCustomFieldRequest;
import com.deanmanagement.testmanagement.project.internal.entity.AuditAction;
import com.deanmanagement.testmanagement.project.internal.entity.AuditEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldDefinition;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldType;
import com.deanmanagement.testmanagement.project.internal.repository.CustomFieldDefinitionRepository;
import com.deanmanagement.testmanagement.project.internal.repository.CustomFieldValueRepository;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.shared.exception.ConflictException;
import com.deanmanagement.testmanagement.shared.exception.DuplicateKeyException;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Custom field definitions (PRD-035). Values are written by {@link CustomFieldValueWriter}. The
 * caps keep forms usable and filter queries bounded; they are deliberately not configurable.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomFieldService {

    public static final int MAX_ACTIVE_FIELDS_PER_ENTITY_TYPE = 20;
    public static final int MAX_OPTIONS = 50;

    private final CustomFieldDefinitionRepository definitionRepository;
    private final CustomFieldValueRepository valueRepository;
    private final ProjectRepository projectRepository;
    private final AuditService auditService;

    /** Every definition, archived included, in display order; {@code entityType} null means all. */
    public List<CustomFieldResponse> list(UUID projectId, CustomFieldEntityType entityType) {
        List<CustomFieldDefinition> definitions = entityType == null
                ? definitionRepository.findByProjectIdOrderByEntityTypeAscOrderIndexAscNameAsc(projectId)
                : definitionRepository.findByProjectIdAndEntityTypeOrderByOrderIndexAscNameAsc(projectId, entityType);
        return definitions.stream().map(CustomFieldService::toResponse).toList();
    }

    @Transactional
    public CustomFieldResponse create(UUID projectId, CreateCustomFieldRequest request, UUID userId) {
        requireRoomFor(projectId, request.entityType());
        String name = request.name().trim();
        requireNameFree(projectId, request.entityType(), name, null);

        CustomFieldDefinition field = new CustomFieldDefinition();
        field.setProject(projectRepository.getReferenceById(projectId));
        field.setEntityType(request.entityType());
        field.setName(name);
        field.setFieldType(request.fieldType());
        field.setOptions(validOptions(request.fieldType(), request.options()));
        field.setRequired(Boolean.TRUE.equals(request.required()));
        field.setOrderIndex(definitionRepository.maxOrderIndex(projectId, request.entityType()) + 1);
        field = definitionRepository.save(field);
        audit(projectId, userId, AuditAction.CREATED, field, "Custom field created");
        return toResponse(field);
    }

    @Transactional
    public CustomFieldResponse update(UUID projectId, UUID id, UpdateCustomFieldRequest request, UUID userId) {
        CustomFieldDefinition field = require(projectId, id);
        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new IllegalArgumentException("Custom field name must not be blank");
            }
            String name = request.name().trim();
            requireNameFree(projectId, field.getEntityType(), name, id);
            field.setName(name);
        }
        if (request.fieldType() != null && request.fieldType() != field.getFieldType()) {
            if (valueRepository.countByFieldId(id) > 0) {
                throw new IllegalArgumentException("Custom field '" + field.getName()
                        + "' holds values, so its type can't change; create a new field and archive this one");
            }
            field.setFieldType(request.fieldType());
            if (request.options() == null) {
                field.setOptions(validOptions(field.getFieldType(), field.getOptions()));
            }
        }
        if (request.options() != null || request.renamedOptions() != null) {
            List<String> options = request.options() != null ? request.options() : field.getOptions();
            replaceOptions(field, validOptions(field.getFieldType(), options), request.renamedOptions());
        }
        if (request.required() != null) {
            field.setRequired(request.required());
        }
        if (request.orderIndex() != null) {
            field.setOrderIndex(request.orderIndex());
        }
        field = definitionRepository.save(field);
        audit(projectId, userId, AuditAction.UPDATED, field, "Custom field updated");
        return toResponse(field);
    }

    /** Archiving hides a field from forms and filters and keeps its values (PRD-035 §4). */
    @Transactional
    public CustomFieldResponse archive(UUID projectId, UUID id, UUID userId) {
        CustomFieldDefinition field = require(projectId, id);
        if (!field.isArchived()) {
            field.setArchived(true);
            audit(projectId, userId, AuditAction.UPDATED, field, "Custom field archived");
        }
        return toResponse(field);
    }

    @Transactional
    public CustomFieldResponse unarchive(UUID projectId, UUID id, UUID userId) {
        CustomFieldDefinition field = require(projectId, id);
        if (field.isArchived()) {
            requireRoomFor(projectId, field.getEntityType());
            field.setArchived(false);
            audit(projectId, userId, AuditAction.UPDATED, field, "Custom field restored");
        }
        return toResponse(field);
    }

    /** Deletes a field that holds no values; one that does must be archived or force-deleted. */
    @Transactional
    public void delete(UUID projectId, UUID id, UUID userId) {
        CustomFieldDefinition field = require(projectId, id);
        long values = valueRepository.countByFieldId(id);
        if (values > 0) {
            throw new ConflictException("Custom field '" + field.getName() + "' holds " + values
                    + " value(s); archive it instead, or delete with force=true to discard them");
        }
        definitionRepository.delete(field);
        audit(projectId, userId, AuditAction.DELETED, field, "Custom field deleted");
    }

    /** Deletes a field and, through the database cascade, every value it holds. */
    @Transactional
    public void forceDelete(UUID projectId, UUID id, UUID userId) {
        CustomFieldDefinition field = require(projectId, id);
        long values = valueRepository.countByFieldId(id);
        definitionRepository.delete(field);
        audit(projectId, userId, AuditAction.DELETED, field,
                "Custom field force-deleted with " + values + " value(s)");
    }

    /**
     * Applies a new option list. A renamed option's stored values are rewritten; an option that
     * disappears without a rename must be unused (PRD-035 §4).
     */
    private void replaceOptions(CustomFieldDefinition field, List<String> options, Map<String, String> renames) {
        Map<String, String> renamed = renames != null ? renames : Map.of();
        List<String> current = field.getOptions();
        for (Map.Entry<String, String> rename : renamed.entrySet()) {
            if (!current.contains(rename.getKey()) || !options.contains(rename.getValue())) {
                throw new IllegalArgumentException("Rename '" + rename.getKey() + "' -> '" + rename.getValue()
                        + "' must name a current option and one in the new list");
            }
            if (renamed.containsKey(rename.getValue()) && !rename.getKey().equals(rename.getValue())) {
                throw new IllegalArgumentException("Option '" + rename.getValue() + "' is both renamed and a rename target");
            }
        }
        Set<String> removed = new HashSet<>(current);
        removed.removeAll(options);
        removed.removeAll(renamed.keySet());
        if (!removed.isEmpty() && field.getId() != null) {
            List<String> inUse = valueRepository.findUsedOptions(field.getId(), removed);
            if (!inUse.isEmpty()) {
                throw new ConflictException("Option(s) in use can't be removed: " + String.join(", ", inUse)
                        + "; rename them instead, or archive the field");
            }
        }
        renamed.forEach((oldLabel, newLabel) -> valueRepository.renameOption(field.getId(), oldLabel, newLabel));
        field.setOptions(options);
    }

    /** Options for a select type: 1..50 distinct (ignoring case), non-blank, trimmed labels. Others have none. */
    private static List<String> validOptions(CustomFieldType type, List<String> requested) {
        if (!type.hasOptions()) {
            return new ArrayList<>();
        }
        if (requested == null || requested.isEmpty()) {
            throw new IllegalArgumentException(type + " fields need at least one option");
        }
        if (requested.size() > MAX_OPTIONS) {
            throw new IllegalArgumentException("A field can have at most " + MAX_OPTIONS + " options");
        }
        List<String> options = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String option : requested) {
            String label = option == null ? "" : option.trim();
            if (label.isEmpty()) {
                throw new IllegalArgumentException("Options must not be blank");
            }
            if (label.length() > CustomFieldValueWriter.MAX_TEXT_LENGTH) {
                throw new IllegalArgumentException("Options must be at most "
                        + CustomFieldValueWriter.MAX_TEXT_LENGTH + " characters");
            }
            if (label.contains(CustomFieldValueWriter.MULTI_SELECT_SEPARATOR)) {
                throw new IllegalArgumentException("Option '" + label + "' must not contain '"
                        + CustomFieldValueWriter.MULTI_SELECT_SEPARATOR + "'");
            }
            if (!seen.add(CustomFieldValueWriter.normalize(label))) {
                throw new IllegalArgumentException("Duplicate option '" + label + "'");
            }
            options.add(label);
        }
        return options;
    }

    private void requireRoomFor(UUID projectId, CustomFieldEntityType entityType) {
        if (definitionRepository.countByProjectIdAndEntityTypeAndArchivedFalse(projectId, entityType)
                >= MAX_ACTIVE_FIELDS_PER_ENTITY_TYPE) {
            throw new IllegalArgumentException("A project can have at most " + MAX_ACTIVE_FIELDS_PER_ENTITY_TYPE
                    + " active custom fields per entity type; archive one first");
        }
    }

    /** The unique key is case-sensitive; names must also differ ignoring case (PRD-035 §4). */
    private void requireNameFree(UUID projectId, CustomFieldEntityType entityType, String name, UUID exceptId) {
        definitionRepository.findByProjectIdAndEntityTypeOrderByOrderIndexAscNameAsc(projectId, entityType).stream()
                .filter(d -> !d.getId().equals(exceptId))
                .filter(d -> CustomFieldValueWriter.normalize(d.getName()).equals(CustomFieldValueWriter.normalize(name)))
                .findFirst()
                .ifPresent(d -> {
                    throw new DuplicateKeyException("custom field name", d.getName());
                });
    }

    private CustomFieldDefinition require(UUID projectId, UUID id) {
        return definitionRepository.findByIdAndProjectId(id, projectId)
                .orElseThrow(() -> new ResourceNotFoundException("CustomField", id));
    }

    private void audit(UUID projectId, UUID userId, AuditAction action, CustomFieldDefinition field, String details) {
        auditService.log(projectId, userId, action, AuditEntityType.PROJECT, projectId,
                field.getEntityType() + " field '" + field.getName() + "'", details);
    }

    private static CustomFieldResponse toResponse(CustomFieldDefinition field) {
        return new CustomFieldResponse(field.getId(), field.getEntityType(), field.getName(), field.getFieldType(),
                List.copyOf(field.getOptions()), field.isRequired(), field.isArchived(), field.getOrderIndex());
    }
}
