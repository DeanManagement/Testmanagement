package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.TestResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.attachment.AttachmentSummary;
import com.deanmanagement.testmanagement.project.internal.dto.report.RunReportOptions;
import com.deanmanagement.testmanagement.project.internal.dto.report.TestRunReportResponse;
import com.deanmanagement.testmanagement.project.internal.repository.ScreenshotRepository;
import com.deanmanagement.testmanagement.project.internal.dto.testSuite.TestSuiteReportResponse;
import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.shared.exception.ResourceNotFoundException;
import com.deanmanagement.testmanagement.project.internal.repository.ProjectRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PdfReportService {

    private final TestRunService testRunService;
    private final TestSuiteService testSuiteService;
    private final ProjectRepository projectRepository;
    private final AttachmentService attachmentService;
    private final ScreenshotRepository screenshotRepository;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    public byte[] generateTestRunReport(UUID projectId, UUID testRunId) {
        return generateTestRunReport(projectId, testRunId, RunReportOptions.RESULTS_ONLY);
    }

    /** With steps and screenshots on request (PRD-048); screenshots are read from the database. */
    public byte[] generateTestRunReport(UUID projectId, UUID testRunId, RunReportOptions options) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        TestRunReportResponse report = testRunService.getReport(projectId, testRunId);

        String html = new RunReportHtml(report,
                attachmentNames(report.results().stream().map(TestResultResponse::testCaseId).toList()),
                options, screenshotRepository::findById, DATE_FMT)
                .build(project.getName(), CSS);
        return renderPdf(html);
    }

    public byte[] generateTestSuiteReport(UUID projectId, UUID suiteId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", projectId));
        TestSuiteReportResponse report = testSuiteService.getReport(projectId, suiteId);

        String html = buildTestSuiteHtml(project.getName(), report, attachmentNames(report.results().stream()
                .map(TestSuiteReportResponse.TestCaseLatestResult::testCaseId).toList()));
        return renderPdf(html);
    }

    /** File names per case, never bytes (PRD-044 §3.6): the report says a file exists, not what is in it. */
    private Map<UUID, List<AttachmentSummary>> attachmentNames(List<UUID> testCaseIds) {
        return attachmentService.summariesByTestCase(testCaseIds.stream().filter(Objects::nonNull).toList());
    }

    private static void appendAttachments(StringBuilder sb, List<AttachmentSummary> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return;
        }
        sb.append("<br/><span class=\"attachments\">Attachments: ")
                .append(escapeHtml(attachments.stream().map(AttachmentSummary::fileName).collect(Collectors.joining(", "))))
                .append("</span>");
    }

    private String buildTestSuiteHtml(String projectName, TestSuiteReportResponse report,
                                      Map<UUID, List<AttachmentSummary>> attachments) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" ");
        sb.append("\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n");
        sb.append("<html xmlns=\"http://www.w3.org/1999/xhtml\">\n<head>\n");
        sb.append("<style>\n").append(CSS).append("\n</style>\n</head>\n<body>\n");

        // Header
        sb.append("<div class=\"header\">\n");
        sb.append("  <h1>").append(escapeHtml(projectName)).append(" — Test Suite Report</h1>\n");
        sb.append("  <p class=\"subtitle\">").append(escapeHtml(report.name())).append("</p>\n");
        if (report.description() != null && !report.description().isBlank()) {
            sb.append("  <p class=\"description\">").append(escapeHtml(report.description())).append("</p>\n");
        }
        sb.append("  <p class=\"meta\">Generated: ").append(DATE_FMT.format(Instant.now())).append("</p>\n");
        sb.append("</div>\n");

        // Stats
        sb.append("<h2>Summary</h2>\n");
        sb.append("<table class=\"stats\">\n<tr>");
        sb.append("<th>Total</th><th>Passed</th><th>Failed</th><th>Blocked</th><th>Skipped</th><th>Untested</th><th>Pass Rate</th><th>Progress</th>");
        sb.append("</tr>\n<tr>");
        sb.append("<td>").append(report.total()).append("</td>");
        sb.append("<td class=\"passed\">").append(report.passed()).append("</td>");
        sb.append("<td class=\"failed\">").append(report.failed()).append("</td>");
        sb.append("<td class=\"blocked\">").append(report.blocked()).append("</td>");
        sb.append("<td class=\"skipped\">").append(report.skipped()).append("</td>");
        sb.append("<td>").append(report.untested()).append("</td>");
        sb.append("<td><strong>").append(percent(report.passRate())).append("</strong></td>");
        sb.append("<td>").append(percent(report.progress())).append("</td>");
        sb.append("</tr>\n</table>\n");

        // Results table
        sb.append("<h2>Test Cases</h2>\n");
        sb.append("<table class=\"results\">\n<tr>");
        sb.append("<th>Test Case</th><th>Status</th><th>From Run</th><th>Date</th>");
        sb.append("</tr>\n");
        for (TestSuiteReportResponse.TestCaseLatestResult result : report.results()) {
            sb.append("<tr>");
            sb.append("<td>").append(escapeHtml(result.testCaseTitle()));
            appendAttachments(sb, attachments.get(result.testCaseId()));
            sb.append("</td>");
            if (result.status() != null) {
                sb.append("<td class=\"").append(result.status().name().toLowerCase()).append("\">");
                sb.append(result.status()).append("</td>");
            } else {
                sb.append("<td>Untested</td>");
            }
            sb.append("<td>").append(result.testRunName() != null ? escapeHtml(result.testRunName()) : "-").append("</td>");
            sb.append("<td>").append(result.updatedAt() != null ? DATE_FMT.format(result.updatedAt()) : "-").append("</td>");
            sb.append("</tr>\n");
        }
        sb.append("</table>\n");

        sb.append("</body>\n</html>");
        return sb.toString();
    }

    private byte[] renderPdf(String html) {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF report", e);
        }
    }

    /** One decimal and a percent sign; "–" when there is no figure, e.g. nothing executed (PRD-049). */
    static String percent(Double value) {
        return value == null ? "–" : String.format("%.1f%%", value);
    }

    static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static final String CSS = """
            body {
                font-family: Helvetica, Arial, sans-serif;
                font-size: 11px;
                color: #333;
                margin: 30px;
            }
            .header {
                border-bottom: 2px solid #1976d2;
                padding-bottom: 10px;
                margin-bottom: 20px;
            }
            .header h1 {
                font-size: 18px;
                color: #1976d2;
                margin: 0 0 4px 0;
            }
            .subtitle {
                font-size: 14px;
                font-weight: bold;
                margin: 0 0 4px 0;
            }
            .description {
                color: #666;
                margin: 0 0 4px 0;
            }
            .meta {
                color: #999;
                font-size: 10px;
                margin: 0;
            }
            h2 {
                font-size: 14px;
                color: #1976d2;
                margin: 20px 0 8px 0;
            }
            table {
                width: 100%;
                border-collapse: collapse;
                margin-bottom: 16px;
            }
            th, td {
                border: 1px solid #ddd;
                padding: 6px 8px;
                text-align: left;
            }
            th {
                background-color: #f5f5f5;
                font-weight: bold;
            }
            tr:nth-child(even) td {
                background-color: #fafafa;
            }
            .summary td {
                border: none;
                padding: 4px 12px 4px 0;
            }
            .stats th, .stats td {
                text-align: center;
            }
            .passed { color: #4caf50; font-weight: bold; }
            .failed { color: #f44336; font-weight: bold; }
            .blocked { color: #ff9800; font-weight: bold; }
            .skipped { color: #9e9e9e; font-weight: bold; }
            .pending { color: #2196f3; font-weight: bold; }
            .unapproved { color: #b45309; font-size: 9px; }
            .attachments { color: #555; font-size: 9px; }
            .key { white-space: nowrap; font-weight: bold; }
            table.steps { margin: 0; font-size: 9px; }
            table.steps th { background: #f5f5f5; }
            img.screenshot { max-width: 220px; max-height: 160px; }
            """;
}
