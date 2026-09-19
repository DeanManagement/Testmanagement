package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.shared.CsvCells;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.deanmanagement.testmanagement.project.internal.dto.attachment.AttachmentSummary;
import com.deanmanagement.testmanagement.project.internal.dto.TestStepRequest;
import com.deanmanagement.testmanagement.project.internal.dto.io.ImportResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.io.ImportResultResponse.ImportError;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.CreateTestCaseRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseMapper;
import com.deanmanagement.testmanagement.project.internal.dto.testCase.TestCaseResponse;
import com.deanmanagement.testmanagement.project.internal.dto.customField.CustomFieldResponse;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseFolderRepository;
import com.deanmanagement.testmanagement.project.internal.repository.SharedStepRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestCaseRepository;
import com.deanmanagement.testmanagement.project.internal.entity.SharedStep;
import com.deanmanagement.testmanagement.project.internal.service.gherkin.GherkinImporter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Import/export of a project's test cases as JSON or CSV (PRD-004), and Gherkin import (PRD-040). Import validates per row and
 * supports a dry-run that persists nothing. Limited to {@value #MAX_IMPORT_ROWS} rows per file.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TestCaseImportExportService {

    public static final int MAX_IMPORT_ROWS = 500;
    private static final String[] CSV_HEADERS =
            {"title", "description", "preconditions", "priority", "status", "labels", "steps", "estimateMinutes"};
    private static final String STEP_PAIR_SEPARATOR = ";;";
    private static final String LABEL_SEPARATOR = ";";
    private static final int MIN_ESTIMATE_MINUTES = 1;
    private static final int MAX_ESTIMATE_MINUTES = 1440;
    /** Custom field columns are named {@code cf:<Field name>} (PRD-035 §3.7). */
    private static final String CUSTOM_FIELD_COLUMN_PREFIX = "cf:";

    private final TestCaseRepository testCaseRepository;
    private final TestCaseMapper testCaseMapper;
    private final TestCaseService testCaseService;
    private final ObjectMapper objectMapper;
    private final ProjectRepository projectRepository;
    private final CustomFieldService customFieldService;
    private final CustomFieldValueWriter customFieldWriter;
    private final TestCaseFolderRepository folderRepository;
    private final GherkinImporter gherkinImporter;
    private final SharedStepRepository sharedStepRepository;
    private final AttachmentService attachmentService;

    // ---- Export ----

    public byte[] exportJson(UUID projectId) {
        List<TestCase> entities = testCaseRepository.findByProjectIdWithSteps(projectId);
        // Metadata only (PRD-044 §3.6): an export stays a readable text file, and import ignores it.
        Map<UUID, List<AttachmentSummary>> attachments =
                attachmentService.summariesByTestCase(entities.stream().map(TestCase::getId).toList());
        List<TestCaseResponse> cases = entities.stream()
                .map(tc -> testCaseMapper.toDetailResponse(tc, null, attachments.getOrDefault(tc.getId(), List.of())))
                .toList();
        return objectMapper.writeValueAsString(cases).getBytes(StandardCharsets.UTF_8);
    }

    public byte[] exportCsv(UUID projectId, boolean excel) {
        StringWriter sw = new StringWriter();
        if (excel) {
            sw.write('﻿'); // UTF-8 BOM so Excel detects encoding
        }
        // Archived fields too: their values are kept, so a round trip must not drop them (PRD-035 §4).
        List<String> fieldNames = customFieldService.list(projectId, CustomFieldEntityType.TEST_CASE).stream()
                .map(CustomFieldResponse::name)
                .toList();
        List<String> headers = new ArrayList<>(List.of(CSV_HEADERS));
        fieldNames.forEach(name -> headers.add(CUSTOM_FIELD_COLUMN_PREFIX + name));
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(headers.toArray(String[]::new)).build();
        try (CSVPrinter printer = new CSVPrinter(sw, format)) {
            for (TestCaseResponse tc : testCaseRepository.findByProjectIdWithSteps(projectId).stream()
                    .map(testCaseMapper::toResponse).toList()) {
                List<Object> cells = new ArrayList<>(Arrays.<Object>asList(
                        CsvCells.safe(tc.title()),
                        CsvCells.safe(tc.description()),
                        CsvCells.safe(tc.preconditions()),
                        tc.priority(),
                        tc.status(),
                        CsvCells.safe(tc.labels() == null ? "" : String.join(LABEL_SEPARATOR, tc.labels())),
                        CsvCells.safe(encodeSteps(tc)),
                        tc.estimateMinutes()));
                fieldNames.forEach(name -> cells.add(customFieldCell(tc.customFields().get(name))));
                printer.printRecord(cells);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return sw.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Multi-select options joined by ';'. Numbers skip csvSafe: a negative one is not a formula. */
    private static String customFieldCell(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal number) {
            return number.toPlainString();
        }
        if (value instanceof List<?> options) {
            return CsvCells.safe(options.stream().map(Object::toString)
                    .collect(Collectors.joining(CustomFieldValueWriter.MULTI_SELECT_SEPARATOR)));
        }
        return CsvCells.safe(value.toString());
    }

    /** Shared steps expanded: a CSV cell has no room for a reference (PRD-030), so this is lossy. */
    private String encodeSteps(TestCaseResponse tc) {
        if (tc.steps() == null || tc.steps().isEmpty()) {
            return "";
        }
        return tc.steps().stream()
                .flatMap(s -> s.sharedStepId() == null ? Stream.of(s) : s.expandedSteps().stream())
                .map(s -> nullToEmpty(s.action()) + "|" + nullToEmpty(s.expectedResult()))
                .reduce((a, b) -> a + STEP_PAIR_SEPARATOR + b)
                .orElse("");
    }

    // ---- Import ----

    /**
     * Read-write: it used to inherit the class's read-only transaction, which every create joined,
     * so rows were reported imported but never written. Rows are validated before their create,
     * so a refused row is skipped without marking the transaction rollback-only.
     */
    @Transactional
    public ImportResultResponse importData(UUID projectId, String fileName, byte[] content,
                                           boolean dryRun, UUID userId) {
        return importData(projectId, fileName, content, null, dryRun, userId);
    }

    /**
     * {@code folderId}, when set, is where the cases go: Gherkin features become folders under it
     * (PRD-040), CSV/JSON rows land in it directly. A folder of another project is a 404.
     */
    @Transactional
    public ImportResultResponse importData(UUID projectId, String fileName, byte[] content, UUID folderId,
                                           boolean dryRun, UUID userId) {
        boolean reviewRequired = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId))
                .isReviewRequired();
        if (folderId != null) {
            folderRepository.findById(folderId)
                    .filter(f -> f.getProject().getId().equals(projectId))
                    .orElseThrow(() -> new ResourceNotFoundException("TestCaseFolder", folderId));
        }
        if (GherkinImporter.isGherkinUpload(fileName)) {
            return gherkinImporter.importUpload(
                    new GherkinImporter.Target(projectId, folderId, reviewRequired, dryRun, userId), fileName, content);
        }
        String text = stripBom(new String(content, StandardCharsets.UTF_8));
        boolean json = (fileName != null && fileName.toLowerCase().endsWith(".json"))
                || text.stripLeading().startsWith("[");
        List<RowData> rows = json ? parseJson(text, sharedStepIdsByTitle(projectId)) : parseCsv(text);

        if (rows.size() > MAX_IMPORT_ROWS) {
            throw new IllegalArgumentException(
                    "Import exceeds the limit of " + MAX_IMPORT_ROWS + " test cases (" + rows.size() + ")");
        }

        int imported = 0;
        int skipped = 0;
        List<ImportError> errors = new ArrayList<>();
        List<ImportError> warnings = new ArrayList<>();
        for (RowData row : rows) {
            try {
                CreateTestCaseRequest request = toRequest(row, folderId);
                if (reviewRequired && request.status() == TestCaseStatus.ACTIVE) {
                    // Importing is not approving: the row still comes in, waiting for review (PRD-033).
                    request = withStatus(request, TestCaseStatus.IN_REVIEW);
                    warnings.add(new ImportError(row.rowNumber(), "status ACTIVE imported as IN_REVIEW: this project requires review"));
                }
                if (row.attachmentCount() > 0) {
                    // An export carries attachment metadata, not bytes (PRD-044 §3.6); say so rather than
                    // let anyone believe the files came across.
                    warnings.add(new ImportError(row.rowNumber(), row.attachmentCount() + " attachment(s) not imported"));
                }
                // Checked here rather than left to create(), so a dry run reports the same errors and a
                // bad row fails before it reaches the write transaction.
                customFieldWriter.validate(projectId, CustomFieldEntityType.TEST_CASE, request.customFields());
                if (!dryRun) {
                    testCaseService.create(projectId, request, userId, CustomFieldWriteMode.MACHINE);
                }
                imported++;
            } catch (IllegalArgumentException e) {
                skipped++;
                errors.add(new ImportError(row.rowNumber(), e.getMessage()));
            }
        }
        return new ImportResultResponse(imported, skipped, dryRun, errors, warnings);
    }

    /** Raw, unvalidated import row. */
    private record RowData(int rowNumber, String title, String description, String preconditions,
                           String priority, String status, List<String> labels,
                           List<TestStepRequest> steps, Map<String, Object> customFields,
                           String estimateMinutes, List<String> unknownSharedSteps, int attachmentCount) {
    }

    /** Shared steps are referenced by title in a file (PRD-030): ids differ between projects. */
    private Map<String, UUID> sharedStepIdsByTitle(UUID projectId) {
        return sharedStepRepository.findByProjectId(projectId).stream()
                .collect(Collectors.toMap(SharedStep::getTitle, SharedStep::getId));
    }

    private static CreateTestCaseRequest withStatus(CreateTestCaseRequest r, TestCaseStatus status) {
        return new CreateTestCaseRequest(r.title(), r.description(), r.preconditions(), r.priority(), status,
                r.labels(), r.steps(), r.folderId(), r.customFields(), r.estimateMinutes());
    }

    private CreateTestCaseRequest toRequest(RowData row, UUID folderId) {
        if (row.title() == null || row.title().isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        if (!row.unknownSharedSteps().isEmpty()) {
            throw new IllegalArgumentException("unknown shared step(s): " + String.join(", ", row.unknownSharedSteps())
                    + " (create them in this project first)");
        }
        Priority priority = parseEnum(Priority.class, row.priority(), Priority.MEDIUM, "priority");
        TestCaseStatus status = parseEnum(TestCaseStatus.class, row.status(), TestCaseStatus.DRAFT, "status");
        Set<String> labels = row.labels() == null ? Set.of() : new LinkedHashSet<>(row.labels());
        return new CreateTestCaseRequest(row.title().trim(), emptyToNull(row.description()),
                emptyToNull(row.preconditions()), priority, status, labels, row.steps(), folderId, row.customFields(),
                parseEstimate(row.estimateMinutes()));
    }

    /** Blank means "not estimated"; anything else must be whole minutes from 1 to a day (PRD-036). */
    private static Integer parseEstimate(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        // Up to four digits, so parseInt below cannot overflow or throw.
        if (raw.trim().matches("\\d{1,4}")) {
            int minutes = Integer.parseInt(raw.trim());
            if (minutes >= MIN_ESTIMATE_MINUTES && minutes <= MAX_ESTIMATE_MINUTES) {
                return minutes;
            }
        }
        throw new IllegalArgumentException("invalid estimateMinutes: '" + raw + "' (whole minutes, "
                + MIN_ESTIMATE_MINUTES + "-" + MAX_ESTIMATE_MINUTES + ")");
    }

    private <E extends Enum<E>> E parseEnum(Class<E> type, String value, E fallback, String field) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid " + field + ": '" + value + "'");
        }
    }

    private List<RowData> parseCsv(String text) {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .setTrim(true)
                .build();
        List<RowData> rows = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(new StringReader(text), format)) {
            if (!parser.getHeaderMap().containsKey("title")) {
                throw new IllegalArgumentException("CSV must contain a 'title' header column");
            }
            for (CSVRecord record : parser) {
                int rowNumber = (int) record.getRecordNumber() + 1; // +1 for the header line
                rows.add(new RowData(
                        rowNumber,
                        get(record, "title"),
                        get(record, "description"),
                        get(record, "preconditions"),
                        get(record, "priority"),
                        get(record, "status"),
                        parseLabels(get(record, "labels")),
                        parseSteps(get(record, "steps")),
                        customFieldCells(record, parser.getHeaderNames()),
                        get(record, "estimateMinutes"),
                        List.of(),
                        0
                ));
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read CSV: " + e.getMessage());
        }
        return rows;
    }

    /** Blank cells are left out, so an empty column never clears or fails anything. */
    private Map<String, Object> customFieldCells(CSVRecord record, List<String> headers) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String header : headers) {
            String value = header.startsWith(CUSTOM_FIELD_COLUMN_PREFIX) ? get(record, header) : null;
            if (value != null && !value.isBlank()) {
                values.put(header.substring(CUSTOM_FIELD_COLUMN_PREFIX.length()), value);
            }
        }
        return values;
    }

    private String get(CSVRecord record, String column) {
        return record.isMapped(column) && record.isSet(column) ? record.get(column) : null;
    }

    private List<String> parseLabels(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<String> labels = new ArrayList<>();
        for (String part : raw.split(LABEL_SEPARATOR)) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                labels.add(trimmed);
            }
        }
        return labels;
    }

    private List<TestStepRequest> parseSteps(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<TestStepRequest> steps = new ArrayList<>();
        for (String pair : raw.split(STEP_PAIR_SEPARATOR)) {
            if (pair.isBlank()) {
                continue;
            }
            int sep = pair.indexOf('|');
            String action = (sep >= 0 ? pair.substring(0, sep) : pair).trim();
            String expected = sep >= 0 ? pair.substring(sep + 1).trim() : "";
            if (action.isEmpty()) {
                throw new IllegalArgumentException("invalid step (missing action): '" + pair + "'");
            }
            steps.add(new TestStepRequest(action, expected, null));
        }
        return steps;
    }

    private List<RowData> parseJson(String text, Map<String, UUID> sharedStepIds) {
        JsonItem[] items;
        try {
            items = objectMapper.readValue(text, JsonItem[].class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not parse JSON: " + e.getMessage());
        }
        List<RowData> rows = new ArrayList<>();
        for (int i = 0; i < items.length; i++) {
            JsonItem item = items[i];
            List<TestStepRequest> steps = new ArrayList<>();
            List<String> unknown = new ArrayList<>();
            if (item.steps() != null) {
                for (JsonItem.Step s : item.steps()) {
                    if (s != null && s.sharedStepTitle() != null) {
                        UUID id = sharedStepIds.get(s.sharedStepTitle());
                        if (id == null) {
                            unknown.add("'" + s.sharedStepTitle() + "'");
                        } else {
                            steps.add(new TestStepRequest(null, null, null, id));
                        }
                    } else if (s != null && s.action() != null && !s.action().isBlank()) {
                        steps.add(new TestStepRequest(s.action(), s.expectedResult(), s.testData()));
                    }
                }
            }
            rows.add(new RowData(i + 1, item.title(), item.description(), item.preconditions(),
                    item.priority(), item.status(), item.labels(), steps, item.customFields(),
                    item.estimateMinutes() == null ? null : item.estimateMinutes().toString(), unknown,
                    item.attachments() == null ? 0 : item.attachments().size()));
        }
        return rows;
    }

    /** JSON import shape; server-managed fields (id, key, timestamps) are ignored on read. */
    private record JsonItem(String title, String description, String preconditions, String priority,
                            String status, List<String> labels, List<Step> steps,
                            Map<String, Object> customFields, Object estimateMinutes, List<Object> attachments) {
        /** {@code sharedStepTitle} set: a reference to that shared step of the project (PRD-030). */
        private record Step(String action, String expectedResult, String testData, String sharedStepTitle) {
        }
    }

    private static String stripBom(String s) {
        return (!s.isEmpty() && s.charAt(0) == '﻿') ? s.substring(1) : s;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
