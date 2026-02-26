package com.deanmanagement.testmanagement.project.internal.controller;

import com.deanmanagement.testmanagement.project.internal.entity.AllureReport;
import com.deanmanagement.testmanagement.project.internal.entity.TestRun;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectMemberRepository;
import com.deanmanagement.testmanagement.project.internal.repository.TestRunRepository;
import com.deanmanagement.testmanagement.project.internal.service.AllureReportService;
import com.deanmanagement.testmanagement.project.internal.service.AllureReportService.AllureReportFile;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
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

    private final AllureReportService allureReportService;
    private final ProjectMemberRepository projectMemberRepository;
    private final TestRunRepository testRunRepository;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
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

    @GetMapping("/view/**")
    public ResponseEntity<byte[]> viewFile(@PathVariable UUID projectId,
                                           @PathVariable String testRunKey,
                                           HttpServletRequest request,
                                           HttpServletResponse response,
                                           Authentication authentication) {
        requireProjectAccess(projectId, authentication);

        String prefix = "/api/projects/" + projectId + "/test-runs/" + testRunKey + "/allure-report/view/";
        String filePath = request.getRequestURI().substring(prefix.length());

        if (filePath.isEmpty()) {
            filePath = "index.html";
        }

        if (request.getParameter("token") != null) {
            String cookiePath = "/api/projects/" + projectId + "/test-runs/" + testRunKey + "/allure-report/view/";
            Cookie cookie = new Cookie("allure_session", request.getParameter("token"));
            cookie.setPath(cookiePath);
            cookie.setHttpOnly(true);
            cookie.setMaxAge(300);
            response.addCookie(cookie);
        }

        AllureReportFile reportFile = allureReportService.getFileFromReport(testRunKey, filePath);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(reportFile.contentType()))
                .body(reportFile.content());
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID projectId,
                       @PathVariable String testRunKey) {
        TestRun testRun = testRunRepository.findByKey(testRunKey)
                .orElseThrow(() -> new ResourceNotFoundException("TestRun", testRunKey));
        allureReportService.deleteByTestRunId(testRun.getId());
    }

    private void requireProjectAccess(UUID projectId, Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!isAdmin && !projectMemberRepository.existsByUserIdAndProjectId(userId, projectId)) {
            throw new AccessDeniedException("User does not have access to this project");
        }
    }
}
