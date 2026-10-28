package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.io.ImportResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.io.ImportResultResponse.ImportError;
import com.deanmanagement.testmanagement.project.internal.dto.requirement.SaveRequirementRequest;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.Requirement;
import com.deanmanagement.testmanagement.project.internal.entity.RequirementStatus;
import com.deanmanagement.testmanagement.project.internal.repository.RequirementRepository;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * CSV import of requirements (TM19), in the style of the test-case import (PRD-004): every row is
 * validated before anything is written, a bad row is reported and skipped, and a dry run reports
 * exactly what a real import would do.
 *
 * <p>Rows upsert by {@code externalId}. Columns: externalId and title (required), description,
 * parentExternalId, status, priority, and {@code cf:<field name>} per custom field. A present
 * description or parentExternalId column is authoritative — a blank cell clears it, so the file can
 * move a requirement back to the top level; a blank status, priority or custom field cell leaves
 * the current value (or the default for a new requirement). A parent may be an existing requirement
 * or any row of the file, in any order; a row whose parent cannot be found, or that would close a
 * cycle, is skipped.
 */
@Service
@RequiredArgsConstructor
public class RequirementImportService {

    public static final int MAX_IMPORT_ROWS = 2000;
    private static final int MAX_EXTERNAL_ID_LENGTH = 100;
    private static final int MAX_TITLE_LENGTH = 500;
    private static final String CUSTOM_FIELD_COLUMN_PREFIX = "cf:";
    private static final String EXTERNAL_ID = "externalId";
    private static final String DESCRIPTION = "description";
    private static final String PARENT = "parentExternalId";

    private final RequirementRepository requirementRepository;
    private final RequirementService requirementService;
    private final CustomFieldValueWriter customFieldWriter;

    /** One parsed, individually valid row. {@code hasX}: the column was in the file. */
    private record Row(int number, String externalId, String title, String description, boolean hasDescription,
                       String parentExternalId, boolean hasParent, RequirementStatus status, Priority priority,
                       Map<String, Object> customFields) {
    }

    @Transactional
    public ImportResultResponse importCsv(UUID projectId, byte[] content, boolean dryRun, UUID userId) {
        Map<String, Requirement> existing = new HashMap<>();
        requirementRepository.findByProjectIdOrderByExternalIdAsc(projectId)
                .forEach(r -> existing.put(r.getExternalId(), r));

        List<ImportError> errors = new ArrayList<>();
        Map<String, Row> rows = parse(projectId, content, errors);
        dropSynced(rows, existing, errors);
        dropUnknownParents(rows, existing, errors);
        dropCycles(rows, existing, errors);

        int created = (int) rows.keySet().stream().filter(id -> !existing.containsKey(id)).count();
        int updated = rows.size() - created;
        if (!dryRun) {
            write(projectId, rows.values(), existing, userId);
        }
        errors.sort(Comparator.comparingInt(ImportError::row));
        return new ImportResultResponse(created, errors.size(), dryRun, errors, List.of(), updated, 0);
    }

    // ---- parsing ----------------------------------------------------------

    private Map<String, Row> parse(UUID projectId, byte[] content, List<ImportError> errors) {
        String text = new String(content, StandardCharsets.UTF_8);
        if (!text.isEmpty() && text.charAt(0) == '﻿') {
            text = text.substring(1);
        }
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader().setSkipHeaderRecord(true).setIgnoreSurroundingSpaces(true).setTrim(true).build();
        Map<String, Row> rows = new LinkedHashMap<>();
        try (CSVParser parser = CSVParser.parse(new StringReader(text), format)) {
            if (!parser.getHeaderMap().containsKey(EXTERNAL_ID) || !parser.getHeaderMap().containsKey("title")) {
                throw new IllegalArgumentException("CSV must contain 'externalId' and 'title' header columns");
            }
            List<CSVRecord> records = parser.getRecords();
            if (records.size() > MAX_IMPORT_ROWS) {
                throw new IllegalArgumentException(
                        "Import exceeds the limit of " + MAX_IMPORT_ROWS + " requirements (" + records.size() + ")");
            }
            for (CSVRecord record : records) {
                int number = (int) record.getRecordNumber() + 1; // +1 for the header line
                try {
                    Row row = toRow(projectId, record, number, parser.getHeaderNames());
                    if (rows.containsKey(row.externalId())) {
                        throw new IllegalArgumentException("externalId '" + row.externalId()
                                + "' already appears in row " + rows.get(row.externalId()).number());
                    }
                    rows.put(row.externalId(), row);
                } catch (IllegalArgumentException e) {
                    errors.add(new ImportError(number, e.getMessage()));
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read CSV: " + e.getMessage());
        }
        return rows;
    }

    private Row toRow(UUID projectId, CSVRecord record, int number, List<String> headers) {
        String externalId = blankToNull(get(record, EXTERNAL_ID));
        String title = blankToNull(get(record, "title"));
        if (externalId == null || title == null) {
            throw new IllegalArgumentException("externalId and title are required");
        }
        if (externalId.length() > MAX_EXTERNAL_ID_LENGTH || title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("externalId is limited to " + MAX_EXTERNAL_ID_LENGTH
                    + " characters and title to " + MAX_TITLE_LENGTH);
        }
        String parent = blankToNull(get(record, PARENT));
        if (externalId.equals(parent)) {
            throw new IllegalArgumentException("a requirement cannot be its own parent");
        }
        Map<String, Object> customFields = new LinkedHashMap<>();
        for (String header : headers) {
            String value = header.startsWith(CUSTOM_FIELD_COLUMN_PREFIX) ? blankToNull(get(record, header)) : null;
            if (value != null) {
                customFields.put(header.substring(CUSTOM_FIELD_COLUMN_PREFIX.length()), value);
            }
        }
        customFieldWriter.validate(projectId, CustomFieldEntityType.REQUIREMENT, customFields);
        return new Row(number, externalId, title, blankToNull(get(record, DESCRIPTION)), record.isMapped(DESCRIPTION),
                parent, record.isMapped(PARENT),
                parseEnum(RequirementStatus.class, get(record, "status"), "status"),
                parseEnum(Priority.class, get(record, "priority"), "priority"),
                customFields);
    }

    // ---- hierarchy checks -------------------------------------------------

    /** TM19: a synced requirement belongs to its tracker; the file cannot overwrite it. */
    private static void dropSynced(Map<String, Row> rows, Map<String, Requirement> existing, List<ImportError> errors) {
        for (Row row : List.copyOf(rows.values())) {
            Requirement current = existing.get(row.externalId());
            if (current != null && current.getSource() != null) {
                rows.remove(row.externalId());
                errors.add(new ImportError(row.number(), row.externalId() + " is synced from the issue tracker "
                        + "and cannot be changed by an import"));
            }
        }
    }

    /** Repeats until stable: dropping a row can orphan a row below it. */
    private static void dropUnknownParents(Map<String, Row> rows, Map<String, Requirement> existing,
                                           List<ImportError> errors) {
        boolean dropped = true;
        while (dropped) {
            dropped = false;
            for (Row row : List.copyOf(rows.values())) {
                String parent = row.parentExternalId();
                if (parent != null && !rows.containsKey(parent) && !existing.containsKey(parent)) {
                    rows.remove(row.externalId());
                    errors.add(new ImportError(row.number(), "unknown parentExternalId '" + parent + "'"));
                    dropped = true;
                }
            }
        }
    }

    /**
     * Checks the tree as it would be after the import — existing parents, overridden by the file —
     * and drops every row on a cycle; repeated because a dropped row falls back to its old parent.
     */
    private static void dropCycles(Map<String, Row> rows, Map<String, Requirement> existing, List<ImportError> errors) {
        boolean dropped = true;
        while (dropped) {
            Map<String, String> parentOf = finalParents(rows, existing);
            List<Row> onCycle = rows.values().stream().filter(row -> isOnCycle(row.externalId(), parentOf)).toList();
            onCycle.forEach(row -> {
                rows.remove(row.externalId());
                errors.add(new ImportError(row.number(), "parentExternalId '" + row.parentExternalId()
                        + "' would place " + row.externalId() + " below itself"));
            });
            dropped = !onCycle.isEmpty();
        }
    }

    private static Map<String, String> finalParents(Map<String, Row> rows, Map<String, Requirement> existing) {
        Map<UUID, String> externalIdById = new HashMap<>();
        existing.values().forEach(r -> externalIdById.put(r.getId(), r.getExternalId()));
        Map<String, String> parentOf = new HashMap<>();
        existing.values().forEach(r -> parentOf.put(r.getExternalId(),
                r.getParentId() == null ? null : externalIdById.get(r.getParentId())));
        rows.values().forEach(row -> {
            if (row.hasParent()) {
                parentOf.put(row.externalId(), row.parentExternalId());
            } else {
                parentOf.putIfAbsent(row.externalId(), null);
            }
        });
        return parentOf;
    }

    private static boolean isOnCycle(String externalId, Map<String, String> parentOf) {
        Set<String> seen = new HashSet<>();
        String current = parentOf.get(externalId);
        while (current != null && seen.add(current)) {
            if (current.equals(externalId)) {
                return true;
            }
            current = parentOf.get(current);
        }
        return false;
    }

    // ---- writing ----------------------------------------------------------

    /**
     * Fields first, keeping current parents, then parents set directly: the final tree was checked
     * above, while setting them one by one through the service could trip over a cycle that exists
     * only half-way through a reshuffle.
     */
    private void write(UUID projectId, Iterable<Row> rows, Map<String, Requirement> existing, UUID userId) {
        Map<String, UUID> ids = new HashMap<>();
        existing.forEach((externalId, requirement) -> ids.put(externalId, requirement.getId()));
        for (Row row : rows) {
            Requirement current = existing.get(row.externalId());
            if (current == null) {
                ids.put(row.externalId(), requirementService.create(projectId, new SaveRequirementRequest(
                        row.externalId(), row.title(), row.description(), row.status(), row.priority(),
                        row.customFields(), null), userId, CustomFieldWriteMode.MACHINE).id());
            } else {
                requirementService.update(projectId, current.getId(), new SaveRequirementRequest(
                        row.externalId(), row.title(),
                        row.hasDescription() ? row.description() : current.getDescription(),
                        row.status() == null ? current.getStatus() : row.status(),
                        row.priority() == null ? current.getPriority() : row.priority(),
                        row.customFields(), current.getParentId()), userId, CustomFieldWriteMode.MACHINE);
            }
        }
        for (Row row : rows) {
            if (row.hasParent()) {
                Requirement requirement = requirementRepository.findById(ids.get(row.externalId())).orElseThrow();
                requirement.setParentId(row.parentExternalId() == null ? null : ids.get(row.parentExternalId()));
            }
        }
    }

    // ---- helpers ----------------------------------------------------------

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid " + field + ": '" + value + "'");
        }
    }

    private static String get(CSVRecord record, String column) {
        return record.isMapped(column) && record.isSet(column) ? record.get(column) : null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
