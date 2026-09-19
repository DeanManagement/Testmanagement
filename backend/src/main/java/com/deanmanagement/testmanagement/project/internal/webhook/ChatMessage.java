package com.deanmanagement.testmanagement.project.internal.webhook;

import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.WebhookEventType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Vendor-neutral content of a chat notification (PRD-031), built from a webhook event's
 * {@code data} map. Text is raw here; {@link SlackMessages} and {@link TeamsMessages} escape it for
 * their target. {@code linkUrl} is null when no public base URL is configured.
 */
record ChatMessage(String headline, Outcome outcome, List<Field> fields, List<String> details,
                   String linkLabel, String linkUrl) {

    static final int MAX_TEXT_LENGTH = 150;
    static final int MAX_LISTED_FAILURES = 5;

    enum Outcome { SUCCESS, FAILURE, NEUTRAL }

    record Field(String title, String value) {
    }

    static ChatMessage of(WebhookEventType type, Project project, Map<String, Object> data, String baseUrl) {
        return switch (type) {
            case RUN_STARTED, RUN_COMPLETED, RUN_FAILED -> forRun(type, project, data, baseUrl);
            case BUG_REPORT_CREATED -> forBug(project, data, baseUrl);
            case TEST_FAILED, PLAN_COMPLETED -> forOther(type, project, data);
        };
    }

    static ChatMessage forTest(Project project) {
        return new ChatMessage("🔔 Test message from Testmanagement for project " + project.getKey(),
                Outcome.NEUTRAL, List.of(), List.of(), null, null);
    }

    private static ChatMessage forRun(WebhookEventType type, Project project, Map<String, Object> data,
                                      String baseUrl) {
        int failed = intValue(data.get("failed"));
        String subject = str(data.get("runKey")) + " · " + truncate(str(data.get("name")));
        List<Field> fields = new ArrayList<>();
        addIfPresent(fields, "Environment", data.get("environment"));
        if (type == WebhookEventType.RUN_STARTED) {
            fields.add(new Field("Total", str(data.get("total"))));
            return new ChatMessage("▶️ " + subject + " started", Outcome.NEUTRAL, fields, List.of(),
                    "Open run", runLink(baseUrl, project, data));
        }
        Object passRate = data.get("passRate");
        fields.add(new Field("Passed", data.get("passed") + "/" + data.get("total")
                + (passRate == null ? "" : " (" + passRate + "%)")));
        fields.add(new Field("Failed", str(data.get("failed"))));
        fields.add(new Field("Blocked", str(data.get("blocked"))));
        fields.add(new Field("Skipped", str(data.get("skipped"))));
        boolean hasFailures = failed > 0 || type == WebhookEventType.RUN_FAILED;
        String verb = type == WebhookEventType.RUN_FAILED ? " failed" : " completed";
        return new ChatMessage((hasFailures ? "❌ " : "✅ ") + subject + verb,
                hasFailures ? Outcome.FAILURE : Outcome.SUCCESS, fields, failedTestLines(data, failed),
                "Open run", runLink(baseUrl, project, data));
    }

    private static ChatMessage forBug(Project project, Map<String, Object> data, String baseUrl) {
        List<Field> fields = new ArrayList<>();
        addIfPresent(fields, "Priority", data.get("priority"));
        addIfPresent(fields, "Status", data.get("status"));
        String link = link(baseUrl, project, "bug-reports", data.get("bugReportId"));
        return new ChatMessage("🐞 New bug in " + project.getKey() + ": " + truncate(str(data.get("title"))),
                Outcome.FAILURE, fields, List.of(), "Open bug report", link);
    }

    /** Chat hooks can't subscribe to these today; kept so every event type still renders something. */
    private static ChatMessage forOther(WebhookEventType type, Project project, Map<String, Object> data) {
        String subject = data.get("testCaseTitle") != null ? ": " + truncate(str(data.get("testCaseTitle"))) : "";
        return new ChatMessage(project.getKey() + " · " + type.name() + subject, Outcome.NEUTRAL,
                List.of(), List.of(), null, null);
    }

    private static List<String> failedTestLines(Map<String, Object> data, int failedCount) {
        if (!(data.get("failedTests") instanceof List<?> tests) || tests.isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (Object test : tests.stream().limit(MAX_LISTED_FAILURES).toList()) {
            if (test instanceof Map<?, ?> t) {
                lines.add(str(t.get("key")) + " " + truncate(str(t.get("title"))));
            }
        }
        if (failedCount > lines.size()) {
            lines.add("+" + (failedCount - lines.size()) + " more");
        }
        return lines;
    }

    private static String runLink(String baseUrl, Project project, Map<String, Object> data) {
        return link(baseUrl, project, "test-runs", data.get("runId"));
    }

    private static String link(String baseUrl, Project project, String section, Object id) {
        if (baseUrl == null || baseUrl.isBlank() || id == null) {
            return null;
        }
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return base + "/projects/" + project.getId() + "/" + section + "/" + id;
    }

    private static void addIfPresent(List<Field> fields, String title, Object value) {
        if (value != null && !value.toString().isBlank()) {
            fields.add(new Field(title, truncate(value.toString())));
        }
    }

    private static String truncate(String text) {
        return text.length() > MAX_TEXT_LENGTH ? text.substring(0, MAX_TEXT_LENGTH - 1) + "…" : text;
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }

    private static int intValue(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }
}
