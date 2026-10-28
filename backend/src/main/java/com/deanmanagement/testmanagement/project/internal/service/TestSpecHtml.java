package com.deanmanagement.testmanagement.project.internal.service;

import com.deanmanagement.testmanagement.project.internal.dto.attachment.AttachmentSummary;
import com.deanmanagement.testmanagement.project.internal.dto.customField.CustomFieldValueMaps;
import com.deanmanagement.testmanagement.project.internal.entity.StepImage;
import com.deanmanagement.testmanagement.project.internal.entity.TestCase;
import com.deanmanagement.testmanagement.project.internal.entity.TestStep;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A test specification as XHTML for the PDF renderer (TM12, PRD-053 §4): the given cases in the
 * order given, each with its metadata, description, preconditions and steps as executed (shared
 * steps in place), step images inline and the names of attached files. Text is escaped with its
 * line breaks kept; Markdown is shown as written, not rendered (no Markdown library on the server).
 */
final class TestSpecHtml {

    private static final String NONE = "-";
    private static final long BYTES_PER_MB = 1024L * 1024;
    /** XML 1.0 forbids these control characters; one would abort the whole PDF. */
    private static final String XML_INVALID = "[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]";

    private final List<TestCase> cases;
    private final Map<UUID, String> folderPaths;
    private final Map<UUID, List<AttachmentSummary>> caseFiles;
    private final Map<UUID, List<AttachmentSummary>> stepFiles;
    private final long imageBudgetBytes;
    private final DateTimeFormatter dateFormat;
    private final StringBuilder sb = new StringBuilder();
    private long embeddedBytes;
    private int leftOut;

    /**
     * @param folderPaths      "Parent / Child" per folder id
     * @param caseFiles        each case's own files, by case id
     * @param stepFiles        each step's files, by step id
     * @param imageBudgetBytes step images are embedded until their bytes reach this; the rest are named
     */
    TestSpecHtml(List<TestCase> cases, Map<UUID, String> folderPaths, Map<UUID, List<AttachmentSummary>> caseFiles,
                 Map<UUID, List<AttachmentSummary>> stepFiles, long imageBudgetBytes, DateTimeFormatter dateFormat) {
        this.cases = cases;
        this.folderPaths = folderPaths;
        this.caseFiles = caseFiles;
        this.stepFiles = stepFiles;
        this.imageBudgetBytes = imageBudgetBytes;
        this.dateFormat = dateFormat;
    }

    String build(String projectName, String css) {
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" ")
                .append("\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n")
                .append("<html xmlns=\"http://www.w3.org/1999/xhtml\">\n<head>\n<style>\n").append(css)
                .append("\n</style>\n</head>\n<body>\n")
                .append("<div class=\"header\">\n  <h1>").append(esc(projectName)).append(" — Test Specification</h1>\n")
                .append("  <p class=\"meta\">").append(cases.size()).append(cases.size() == 1 ? " test case" : " test cases")
                .append(" · Generated: ").append(dateFormat.format(Instant.now())).append("</p>\n</div>\n");
        contents();
        String folder = null;
        for (TestCase testCase : cases) {
            String path = folderOf(testCase);
            if (!path.equals(folder)) {
                folder = path;
                sb.append("<h2 class=\"folder\">").append(esc(path)).append("</h2>\n");
            }
            testCase(testCase);
        }
        if (leftOut > 0) {
            sb.append("<p class=\"meta\">").append(leftOut).append(" more step image(s) are not embedded, to keep the PDF ")
                    .append("under ").append(imageBudgetBytes / BYTES_PER_MB).append(" MB of images; see the cases in the app.</p>\n");
        }
        return sb.append("</body>\n</html>").toString();
    }

    private void contents() {
        sb.append("<h2>Contents</h2>\n<table class=\"contents\">\n");
        for (TestCase testCase : cases) {
            sb.append("<tr><td class=\"key\">").append(esc(testCase.getKey())).append("</td><td>")
                    .append(esc(testCase.getTitle())).append("</td><td>").append(esc(folderOf(testCase)))
                    .append("</td></tr>\n");
        }
        sb.append("</table>\n");
    }

    private void testCase(TestCase testCase) {
        sb.append("<div class=\"case\">\n<h3>").append(esc(testCase.getKey())).append(" — ")
                .append(esc(testCase.getTitle())).append("</h3>\n<table class=\"summary\">\n");
        row("Status", Objects.toString(testCase.getStatus(), NONE));
        row("Priority", Objects.toString(testCase.getPriority(), NONE));
        row("Version", "v" + testCase.getCurrentVersion());
        row("Labels", testCase.getLabels().isEmpty() ? NONE
                : testCase.getLabels().stream().sorted().collect(Collectors.joining(", ")));
        row("Estimate", testCase.getEstimateMinutes() == null ? NONE : testCase.getEstimateMinutes() + " min");
        CustomFieldValueMaps.toMap(testCase.getCustomFieldValues()).forEach((name, value) ->
                row(name, value == null ? NONE : value.toString()));
        sb.append("</table>\n");
        section("Description", testCase.getDescription());
        section("Preconditions", testCase.getPreconditions());
        steps(testCase);
        List<AttachmentSummary> files = caseFiles.get(testCase.getId());
        if (files != null && !files.isEmpty()) {
            sb.append("<p class=\"attachments\">Attachments: ").append(esc(names(files))).append("</p>\n");
        }
        sb.append("</div>\n");
    }

    private void steps(TestCase testCase) {
        List<StepExpansion.ExpandedStep> steps = StepExpansion.expand(testCase.getSteps());
        if (steps.isEmpty()) {
            return;
        }
        sb.append("<table class=\"spec-steps\">\n<tr><th>#</th><th>Action</th><th>Test data</th>")
                .append("<th>Expected result</th></tr>\n");
        for (int i = 0; i < steps.size(); i++) {
            TestStep step = steps.get(i).step();
            sb.append("<tr><td>").append(i + 1).append("</td><td>");
            if (steps.get(i).block() != null) {
                sb.append("<span class=\"shared\">").append(esc(steps.get(i).block().getTitle())).append("</span><br/>");
            }
            sb.append(text(step.getAction()));
            image(step.getImage());
            List<AttachmentSummary> files = step.getId() == null ? null : stepFiles.get(step.getId());
            if (files != null && !files.isEmpty()) {
                sb.append("<br/><span class=\"attachments\">Files: ").append(esc(names(files))).append("</span>");
            }
            sb.append("</td><td>").append(text(step.getTestData())).append("</td><td>")
                    .append(text(step.getExpectedResult())).append("</td></tr>\n");
        }
        sb.append("</table>\n");
    }

    /** Embedded while the budget lasts and the renderer can draw it (not WebP); otherwise named. */
    private void image(StepImage image) {
        if (image == null) {
            return;
        }
        boolean drawable = image.getContentType() != null
                && RunReportHtml.EMBEDDABLE.contains(image.getContentType().toLowerCase());
        if (drawable && embeddedBytes + image.getData().length <= imageBudgetBytes) {
            embeddedBytes += image.getData().length;
            sb.append("<br/><img class=\"step-image\" src=\"data:").append(image.getContentType()).append(";base64,")
                    .append(Base64.getEncoder().encodeToString(image.getData())).append("\"/>");
            return;
        }
        if (drawable) {
            leftOut++;
        }
        sb.append("<br/><span class=\"attachments\">Image: ").append(esc(image.getFileName())).append("</span>");
    }

    private void row(String label, String value) {
        sb.append("<tr><td><strong>").append(esc(label)).append("</strong></td><td>").append(esc(value))
                .append("</td></tr>\n");
    }

    private void section(String title, String body) {
        if (body == null || body.isBlank()) {
            return;
        }
        sb.append("<h4>").append(title).append("</h4>\n<p class=\"text\">").append(text(body)).append("</p>\n");
    }

    private String folderOf(TestCase testCase) {
        return testCase.getFolder() == null ? "No folder"
                : folderPaths.getOrDefault(testCase.getFolder().getId(), testCase.getFolder().getName());
    }

    private static String names(List<AttachmentSummary> files) {
        return files.stream().map(AttachmentSummary::fileName).collect(Collectors.joining(", "));
    }

    /** Escaped, line breaks kept. */
    private static String text(String value) {
        return value == null || value.isBlank() ? "" : esc(value).replace("\r\n", "\n").replace("\n", "<br/>");
    }

    private static String esc(String value) {
        return PdfReportService.escapeHtml(value == null ? null : value.replaceAll(XML_INVALID, ""));
    }
}
