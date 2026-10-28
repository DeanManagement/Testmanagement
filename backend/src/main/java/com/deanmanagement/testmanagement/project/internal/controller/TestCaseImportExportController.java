package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import com.deanmanagement.testmanagement.project.internal.dto.io.GherkinPreviewResponse;
import com.deanmanagement.testmanagement.project.internal.dto.io.ImportResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.filter.TestCaseListFilter;
import com.deanmanagement.testmanagement.project.internal.entity.CustomFieldEntityType;
import com.deanmanagement.testmanagement.project.internal.entity.Priority;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestCaseStatus;
import com.deanmanagement.testmanagement.project.internal.service.CustomFieldFilterParser;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseImportExportService;
import com.deanmanagement.testmanagement.project.internal.service.TestSpecificationService;
import com.deanmanagement.testmanagement.project.internal.service.gherkin.GherkinExporter;
import com.deanmanagement.testmanagement.project.internal.service.gherkin.GherkinFileParser;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/test-cases")
@Tag(name = "Test Case Import/Export", description = "Import and export test cases as JSON, CSV, Gherkin or a PDF specification")
@RequiredArgsConstructor
public class TestCaseImportExportController {

    private static final String FEATURE_FORMAT = "feature";
    private static final int MAX_PREVIEW_CHARS = 100_000;

    private final TestCaseImportExportService importExportService;
    private final GherkinExporter gherkinExporter;
    private final TestSpecificationService specificationService;
    private final CustomFieldFilterParser customFieldFilterParser;

    @GetMapping("/export")
    @RequireProjectRole
    public ResponseEntity<byte[]> export(@PathVariable UUID projectId,
                                         @RequestParam(defaultValue = "json") String format,
                                         @RequestParam(defaultValue = "false") boolean excel,
                                         @RequestParam(required = false) UUID folderId) {
        if (FEATURE_FORMAT.equalsIgnoreCase(format)) {
            return exportFeature(projectId, folderId);
        }
        boolean csv = "csv".equalsIgnoreCase(format);
        byte[] body = csv
                ? importExportService.exportCsv(projectId, excel)
                : importExportService.exportJson(projectId);
        String filename = csv ? "test-cases.csv" : "test-cases.json";
        MediaType contentType = csv ? new MediaType("text", "csv") : MediaType.APPLICATION_JSON;
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(contentType)
                .body(body);
    }

    /** PRD-040 §3.4: one .feature file as text, several as a ZIP. {@code folderId} limits it to that folder. */
    private ResponseEntity<byte[]> exportFeature(UUID projectId, UUID folderId) {
        GherkinExporter.ExportFile file = gherkinExporter.export(projectId, folderId);
        MediaType contentType = file.zip()
                ? new MediaType("application", "zip")
                : new MediaType("text", "plain", StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .contentType(contentType)
                .body(file.content());
    }

    /**
     * TM12: the test specification as a PDF. {@code ids} exports the cases selected in the list;
     * without them, the cases the list's filter matches (the same parameters as the list, a folder
     * with {@code includeSubfolders} included).
     */
    @GetMapping("/specification")
    @RequireProjectRole
    public ResponseEntity<byte[]> exportSpecification(@PathVariable UUID projectId,
                                                      @RequestParam(required = false) List<UUID> ids,
                                                      @RequestParam(required = false) String q,
                                                      @RequestParam(required = false) List<TestCaseStatus> status,
                                                      @RequestParam(required = false) List<Priority> priority,
                                                      @RequestParam(required = false) List<String> label,
                                                      @RequestParam(required = false) UUID folderId,
                                                      @RequestParam(defaultValue = "false") boolean includeSubfolders,
                                                      @RequestParam(defaultValue = "false") boolean rootOnly,
                                                      @RequestParam MultiValueMap<String, String> params) {
        TestCaseListFilter filter = new TestCaseListFilter(q, status, priority, label, folderId, includeSubfolders,
                rootOnly, null, customFieldFilterParser.parse(projectId, CustomFieldEntityType.TEST_CASE, params));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"test-specification.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(specificationService.generate(projectId, filter, ids));
    }

    /** Reads one scenario for the case form; writes nothing (PRD-040 §3.6). */
    @PostMapping(value = "/gherkin/preview", consumes = MediaType.TEXT_PLAIN_VALUE)
    @RequireProjectRole(ProjectRole.TESTER)
    public GherkinPreviewResponse previewGherkin(@PathVariable UUID projectId, @RequestBody String text) {
        if (text.length() > MAX_PREVIEW_CHARS) {
            throw new IllegalArgumentException("A scenario may be at most " + MAX_PREVIEW_CHARS + " characters");
        }
        GherkinFileParser.Scenario scenario = GherkinFileParser.parseScenario(text);
        return new GherkinPreviewResponse(scenario.key(), scenario.title(), scenario.description(),
                scenario.preconditions(), scenario.priority(), scenario.labels(), scenario.steps(),
                scenario.parameterSets(), scenario.problems(), scenario.warnings());
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireProjectRole(ProjectRole.TESTER)
    public ImportResultResponse importTestCases(@PathVariable UUID projectId,
                                                @RequestParam("file") MultipartFile file,
                                                @RequestParam(defaultValue = "false") boolean dryRun,
                                                @RequestParam(required = false) UUID folderId,
                                                Authentication authentication) throws IOException {
        UUID userId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        return importExportService.importData(projectId, file.getOriginalFilename(), file.getBytes(), folderId,
                dryRun, userId);
    }
}
