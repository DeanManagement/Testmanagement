package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.StepResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.TestResultResponse;
import com.deanmanagement.testmanagement.project.internal.dto.attachment.AttachmentSummary;
import com.deanmanagement.testmanagement.project.internal.dto.report.RunReportOptions;
import com.deanmanagement.testmanagement.project.internal.dto.report.TestRunReportResponse;
import com.deanmanagement.testmanagement.project.internal.entity.Screenshot;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The run report as XHTML for the PDF renderer (PRD-048): who ran each case, when, against which
 * version, and on request each step with its outcome and screenshot. A report that is evidence
 * must say who executed what, not only whether it passed.
 */
final class RunReportHtml {

    /** Beyond this the PDF says how many were left out; a big run would otherwise be hundreds of MB. */
    static final int MAX_SCREENSHOTS = 200;
    /** The PDF renderer draws these; WebP it does not. */
    private static final Set<String> EMBEDDABLE = Set.of("image/png", "image/jpeg", "image/gif");
    private static final String NONE = "-";
    private static final String UNKNOWN_USER = "Unknown user";

    private final TestRunReportResponse report;
    private final Map<UUID, List<AttachmentSummary>> attachments;
    private final RunReportOptions options;
    private final Function<UUID, Optional<Screenshot>> screenshots;
    private final DateTimeFormatter dateFormat;
    private final StringBuilder sb = new StringBuilder();
    private int embedded;
    private int leftOut;

    RunReportHtml(TestRunReportResponse report, Map<UUID, List<AttachmentSummary>> attachments,
                  RunReportOptions options, Function<UUID, Optional<Screenshot>> screenshots,
                  DateTimeFormatter dateFormat) {
        this.report = report;
        this.attachments = attachments;
        this.options = options;
        this.screenshots = screenshots;
        this.dateFormat = dateFormat;
    }

    String build(String projectName, String css) {
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" ")
                .append("\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n")
                .append("<html xmlns=\"http://www.w3.org/1999/xhtml\">\n<head>\n<style>\n").append(css)
                .append("\n</style>\n</head>\n<body>\n");
        header(projectName);
        summary();
        results();
        if (leftOut > 0) {
            sb.append("<p class=\"meta\">").append(leftOut).append(" more screenshots are not included; see the run in the app.</p>\n");
        }
        sb.append("</body>\n</html>");
        return sb.toString();
    }

    private void header(String projectName) {
        sb.append("<div class=\"header\">\n  <h1>").append(esc(projectName)).append(" — Test Run Report</h1>\n")
                .append("  <p class=\"subtitle\">").append(esc(report.name())).append("</p>\n");
        if (report.testPlanName() != null) {
            sb.append("  <p class=\"description\">Test plan: ").append(esc(report.testPlanName())).append("</p>\n");
        }
        sb.append("  <p class=\"meta\">Generated: ").append(dateFormat.format(Instant.now())).append("</p>\n</div>\n");
    }

    private void summary() {
        sb.append("<h2>Summary</h2>\n<table class=\"summary\">\n<tr>")
                .append("<td><strong>Status</strong></td><td>").append(report.status()).append("</td>")
                .append("<td><strong>Environment</strong></td><td>").append(orNone(report.environment())).append("</td>")
                .append("</tr>\n<tr>")
                .append("<td><strong>Start</strong></td><td>").append(time(report.startTime())).append("</td>")
                .append("<td><strong>End</strong></td><td>").append(time(report.endTime())).append("</td>")
                .append("</tr>\n</table>\n<table class=\"stats\">\n<tr>")
                .append("<th>Total</th><th>Passed</th><th>Failed</th><th>Blocked</th><th>Skipped</th><th>Pending</th><th>Pass Rate</th>")
                .append("</tr>\n<tr>")
                .append("<td>").append(report.total()).append("</td>")
                .append("<td class=\"passed\">").append(report.passed()).append("</td>")
                .append("<td class=\"failed\">").append(report.failed()).append("</td>")
                .append("<td class=\"blocked\">").append(report.blocked()).append("</td>")
                .append("<td class=\"skipped\">").append(report.skipped()).append("</td>")
                .append("<td class=\"pending\">").append(report.pending()).append("</td>")
                .append("<td><strong>").append(String.format("%.1f%%", report.passRate())).append("</strong></td>")
                .append("</tr>\n</table>\n");
    }

    private void results() {
        sb.append("<h2>Test Results</h2>\n<table class=\"results\">\n<tr>")
                .append("<th>Key</th><th>Test Case</th><th>Status</th><th>Executed by</th><th>Executed at</th>")
                .append("<th>Version</th><th>Comment</th><th>Defect Link</th></tr>\n");
        for (TestResultResponse result : report.results()) {
            resultRow(result);
            if (options.includesSteps() && result.stepResults() != null && !result.stepResults().isEmpty()) {
                stepRows(result);
            }
        }
        sb.append("</table>\n");
    }

    private void resultRow(TestResultResponse result) {
        sb.append("<tr><td class=\"key\">").append(esc(result.testCaseKey())).append("</td>")
                .append("<td>").append(esc(result.testCaseTitle()));
        if (report.unapprovedResultIds().contains(result.id())) {
            sb.append("<br/><span class=\"unapproved\">Executed unapproved wording (v")
                    .append(result.executedVersion()).append(")</span>");
        }
        List<AttachmentSummary> files = attachments.get(result.testCaseId());
        if (files != null && !files.isEmpty()) {
            sb.append("<br/><span class=\"attachments\">Attachments: ")
                    .append(esc(files.stream().map(AttachmentSummary::fileName).collect(Collectors.joining(", "))))
                    .append("</span>");
        }
        sb.append("</td>")
                .append("<td class=\"").append(result.status().name().toLowerCase()).append("\">").append(result.status()).append("</td>")
                .append("<td>").append(executor(result)).append("</td>")
                .append("<td>").append(time(result.executedAt())).append("</td>")
                .append("<td>").append(result.executedVersion() == null ? NONE : "v" + result.executedVersion()).append("</td>")
                .append("<td>").append(esc(ResultEvidence.of(result)).replace("\n", "<br/>")).append("</td>")
                .append("<td>").append(orNone(result.defectLink())).append("</td></tr>\n");
    }

    private void stepRows(TestResultResponse result) {
        List<StepResultResponse> steps = result.stepResults().stream()
                .sorted(Comparator.comparingInt(StepResultResponse::orderIndex)).toList();
        sb.append("<tr><td></td><td colspan=\"7\">\n<table class=\"steps\">\n<tr><th>#</th><th>Action</th><th>Status</th>")
                .append("<th>Actual result</th>").append(options.screenshots() ? "<th>Screenshot</th>" : "").append("</tr>\n");
        for (int i = 0; i < steps.size(); i++) {
            StepResultResponse step = steps.get(i);
            sb.append("<tr><td>").append(i + 1).append("</td>")
                    .append("<td>").append(esc(step.action())).append("</td>")
                    .append("<td class=\"").append(step.status().name().toLowerCase()).append("\">").append(step.status()).append("</td>")
                    .append("<td>").append(orNone(step.actualResult())).append("</td>");
            if (options.screenshots()) {
                sb.append("<td>").append(screenshot(step.screenshotId())).append("</td>");
            }
            sb.append("</tr>\n");
        }
        sb.append("</table>\n</td></tr>\n");
    }

    /** The execution screenshot, embedded while under the cap; never the step's reference image. */
    private String screenshot(UUID screenshotId) {
        if (screenshotId == null) {
            return "";
        }
        if (embedded >= MAX_SCREENSHOTS) {
            leftOut++;
            return "(see the app)";
        }
        return screenshots.apply(screenshotId)
                .filter(shot -> shot.getContentType() != null && EMBEDDABLE.contains(shot.getContentType().toLowerCase()))
                .map(shot -> {
                    embedded++;
                    return "<img class=\"screenshot\" src=\"data:" + shot.getContentType() + ";base64,"
                            + Base64.getEncoder().encodeToString(shot.getData()) + "\"/>";
                })
                .orElse("(see the app)");
    }

    private String executor(TestResultResponse result) {
        if (result.executedByName() != null) {
            return esc(result.executedByName());
        }
        return result.executedBy() != null ? UNKNOWN_USER : NONE;
    }

    private String time(Instant instant) {
        return instant == null ? NONE : dateFormat.format(instant);
    }

    private static String orNone(String text) {
        return text == null || text.isBlank() ? NONE : esc(text);
    }

    private static String esc(String text) {
        return PdfReportService.escapeHtml(text);
    }
}
