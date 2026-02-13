package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.dto.testSuite.BulkTestCasesRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testSuite.CreateTestSuiteRequest;
import com.deanmanagement.testmanagement.project.internal.dto.testSuite.TestSuiteReportResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestSuiteResponse;
import com.deanmanagement.testmanagement.project.internal.dto.UpdateTestSuiteRequest;
import com.deanmanagement.testmanagement.project.internal.service.PdfReportService;
import com.deanmanagement.testmanagement.project.internal.service.TestSuiteService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/test-suites")
@Tag(name = "Test Suites", description = "Test suite management endpoints")
@RequiredArgsConstructor
public class TestSuiteController {

    private final TestSuiteService testSuiteService;
    private final PdfReportService pdfReportService;

    @GetMapping
    public List<TestSuiteResponse> findAll(@PathVariable UUID projectId) {
        return testSuiteService.findByProject(projectId);
    }

    @GetMapping("/{id}")
    public TestSuiteResponse findById(@PathVariable UUID projectId, @PathVariable UUID id) {
        return testSuiteService.findById(projectId, id);
    }

    @GetMapping("/{id}/report")
    public TestSuiteReportResponse getReport(@PathVariable UUID projectId, @PathVariable UUID id) {
        return testSuiteService.getReport(projectId, id);
    }

    @GetMapping("/{id}/report/pdf")
    public ResponseEntity<byte[]> getReportPdf(@PathVariable UUID projectId, @PathVariable UUID id) {
        byte[] pdf = pdfReportService.generateTestSuiteReport(projectId, id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "test-suite-report.pdf");
        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TestSuiteResponse create(@PathVariable UUID projectId,
                                    @Valid @RequestBody CreateTestSuiteRequest request,
                                    Authentication authentication) {
        UUID userId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        return testSuiteService.create(projectId, request, userId);
    }

    @PutMapping("/{id}")
    public TestSuiteResponse update(@PathVariable UUID projectId,
                                    @PathVariable UUID id,
                                    @Valid @RequestBody UpdateTestSuiteRequest request,
                                    Authentication authentication) {
        UUID userId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        return testSuiteService.update(projectId, id, request, userId);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID projectId, @PathVariable UUID id,
                       Authentication authentication) {
        UUID userId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        testSuiteService.delete(projectId, id, userId);
    }

    @PostMapping("/{id}/bulk-add")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void bulkAddTestCases(@PathVariable UUID projectId,
                                  @PathVariable UUID id,
                                  @Valid @RequestBody BulkTestCasesRequest request,
                                  Authentication authentication) {
        UUID userId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        testSuiteService.bulkAddTestCases(projectId, id, request.testCaseIds(), userId);
    }

    @PostMapping("/{id}/bulk-remove")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void bulkRemoveTestCases(@PathVariable UUID projectId,
                                     @PathVariable UUID id,
                                     @Valid @RequestBody BulkTestCasesRequest request,
                                     Authentication authentication) {
        UUID userId = authentication != null ? UUID.fromString(authentication.getName()) : null;
        testSuiteService.bulkRemoveTestCases(projectId, id, request.testCaseIds(), userId);
    }
}
