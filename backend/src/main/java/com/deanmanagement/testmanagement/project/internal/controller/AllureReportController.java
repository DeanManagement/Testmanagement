package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.access.RequireProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.AllureReport;
import com.deanmanagement.testmanagement.project.internal.entity.ProjectRole;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import com.deanmanagement.testmanagement.project.internal.service.AllureReportService;
import com.deanmanagement.testmanagement.project.internal.service.AllureReportService.AllureReportFile;
import com.deanmanagement.testmanagement.project.internal.service.AllureViewSessionService;
import com.deanmanagement.testmanagement.shared.exception.ForbiddenException;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/projects/{projectId}/test-runs/{testRunKey}/allure-report")
@Tag(name = "Allure Reports", description = "Upload and serve Allure HTML reports for test runs")
@RequiredArgsConstructor
public class AllureReportController {

    /**
     * PRD-018: uploaded reports are untrusted HTML/JS. The frontend renders them in a
     * sandboxed iframe (opaque origin), and every /view response carries a CSP sandbox so
     * the content is isolated even when opened directly in a tab. An opaque origin sends no
     * cookies or Authorization header, so /view authenticates via a short-lived
     * single-report token in the URL path, minted by POST /session after a normal
     * JWT + project-role check. The JWT itself never appears in a URL.
     */
    private static final String VIEW_CSP = "sandbox allow-scripts allow-popups";

    private final AllureReportService allureReportService;
    private final AllureViewSessionService viewSessionService;
    private final TestRunRepository testRunRepository;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @RequireProjectRole(ProjectRole.TESTER)
    public Map<String, UUID> upload(@PathVariable UUID projectId,
                                    @PathVariable String testRunKey,
                                    @RequestParam MultipartFile file) throws IOException {
        TestRun testRun = testRunRepository.findByKey(testRunKey)
                .orElseThrow(() -> new ResourceNotFoundException("TestRun", testRunKey));
        AllureReport report = allureReportService.upload(
                testRun.getId(),
                file.getOriginalFilename(),
                file.getBytes()
        );
        return Map.of("id", report.getId());
    }

    /** Mints a short-lived view session for this run's report (requires VIEWER, PRD-018). */
    @PostMapping("/session")
    @RequireProjectRole(ProjectRole.VIEWER)
    public Map<String, String> createViewSession(@PathVariable UUID projectId,
                                                 @PathVariable String testRunKey) {
        testRunRepository.findByKey(testRunKey)
                .orElseThrow(() -> new ResourceNotFoundException("TestRun", testRunKey));
        return Map.of("token", viewSessionService.create(testRunKey));
    }

    @GetMapping("/view/{viewToken}/**")
    public ResponseEntity<byte[]> viewFile(@PathVariable UUID projectId,
                                           @PathVariable String testRunKey,
                                           @PathVariable String viewToken,
                                           HttpServletRequest request) {
        if (!viewSessionService.isValid(viewToken, testRunKey)) {
            throw new ForbiddenException("Invalid or expired report view session");
        }

        String prefix = "/api/projects/" + projectId + "/test-runs/" + testRunKey
                + "/allure-report/view/" + viewToken + "/";
        String filePath = request.getRequestURI().length() > prefix.length()
                ? request.getRequestURI().substring(prefix.length())
                : "";

        if (filePath.isEmpty()) {
            filePath = "index.html";
        }

        AllureReportFile reportFile = allureReportService.getFileFromReport(testRunKey, filePath);

        return ResponseEntity.ok()
                .header("Content-Security-Policy", VIEW_CSP)
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(reportFile.contentType()))
                .body(reportFile.content());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequireProjectRole(ProjectRole.TESTER)
    public void delete(@PathVariable UUID projectId,
                       @PathVariable String testRunKey) {
        TestRun testRun = testRunRepository.findByKey(testRunKey)
                .orElseThrow(() -> new ResourceNotFoundException("TestRun", testRunKey));
        allureReportService.deleteByTestRunId(testRun.getId());
    }
}
