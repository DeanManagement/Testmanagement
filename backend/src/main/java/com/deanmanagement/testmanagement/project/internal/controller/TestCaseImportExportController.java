package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import com.deanmanagement.testmanagement.project.internal.dto.io.ImportResultResponse;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.service.TestCaseImportExportService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/test-cases")
@Tag(name = "Test Case Import/Export", description = "Import and export test cases as JSON or CSV")
@RequiredArgsConstructor
public class TestCaseImportExportController {

    private final TestCaseImportExportService importExportService;

    @GetMapping("/export")
    @RequireProjectRole
    public ResponseEntity<byte[]> export(@PathVariable UUID projectId,
                                         @RequestParam(defaultValue = "json") String format,
                                         @RequestParam(defaultValue = "false") boolean excel) {
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

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequireProjectRole(ProjectRole.TESTER)
    public ImportResultResponse importTestCases(@PathVariable UUID projectId,
                                                @RequestParam("file") MultipartFile file,
                                                @RequestParam(defaultValue = "false") boolean dryRun,
                                                Authentication authentication) throws IOException {
        UUID userId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        return importExportService.importData(projectId, file.getOriginalFilename(), file.getBytes(), dryRun, userId);
    }
}
