package com.deanmanagement.testmanagement.project.internal.webhook;

import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.WebhookEventType;
import com.deanmanagement.testmanagement.project.internal.entity.WebhookFormat;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookPayloadBuilderTest {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();
    private static final String BASE_URL = "https://tm.example.com/";
    private static final UUID RUN_ID = UUID.randomUUID();
    /** RunEventPublisher sends at most this many failed tests in the event data. */
    private static final int MAX_FAILED_TESTS_IN_EVENT = 10;

    private final Project project = project();

    private static Project project() {
        Project project = new Project();
        project.setId(UUID.randomUUID());
        project.setKey("PROJ");
        project.setName("Project");
        return project;
    }

    private static WebhookPayloadBuilder builder(String baseUrl) {
        return new WebhookPayloadBuilder(MAPPER, baseUrl, "");
    }

    private static Map<String, Object> runData(String name, int passed, int failed) {
        List<Map<String, Object>> failedTests = new ArrayList<>();
        IntStream.range(0, Math.min(failed, MAX_FAILED_TESTS_IN_EVENT))
                .forEach(i -> failedTests.add(Map.of("key", "PROJ-" + i, "title", "Test " + i)));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runId", RUN_ID.toString());
        data.put("runKey", "PROJ-Run-12");
        data.put("name", name);
        data.put("environment", "staging");
        data.put("total", passed + failed);
        data.put("passed", passed);
        data.put("failed", failed);
        data.put("blocked", 0);
        data.put("skipped", 0);
        data.put("passRate", 95.8);
        data.put("failedTests", failedTests);
        return data;
    }

    private JsonNode build(WebhookFormat format, WebhookEventType type, Map<String, Object> data, String baseUrl) {
        return MAPPER.readTree(builder(baseUrl).build(format, type, project, data));
    }

    @Nested
    class Generic {

        @Test
        void keepsTheOriginalEnvelope() {
            JsonNode body = build(WebhookFormat.GENERIC, WebhookEventType.RUN_COMPLETED,
                    Map.of("runKey", "PROJ-Run-12"), BASE_URL);

            assertThat(body.propertyNames()).containsExactly(
                    "event", "projectId", "projectKey", "projectName", "timestamp", "data");
            assertThat(body.get("event").asString()).isEqualTo("RUN_COMPLETED");
            assertThat(body.get("data").get("runKey").asString()).isEqualTo("PROJ-Run-12");
        }

        @Test
        void testMessageKeepsTheTestFlag() {
            JsonNode body = MAPPER.readTree(builder(BASE_URL)
                    .buildTest(WebhookFormat.GENERIC, WebhookEventType.RUN_COMPLETED, project));

            assertThat(body.get("data").get("test").asBoolean()).isTrue();
        }
    }

    @Nested
    class Slack {

        @Test
        void completedRunWithoutFailuresIsGreenWithDeepLink() {
            JsonNode attachment = build(WebhookFormat.SLACK, WebhookEventType.RUN_COMPLETED,
                    runData("Nightly regression", 10, 0), BASE_URL).get("attachments").get(0);

            assertThat(attachment.get("color").asString()).isEqualTo("#2e7d32");
            assertThat(attachment.get("title").asString())
                    .isEqualTo("✅ PROJ-Run-12 · Nightly regression completed");
            assertThat(attachment.get("title_link").asString())
                    .isEqualTo("https://tm.example.com/projects/" + project.getId() + "/test-runs/" + RUN_ID);
        }

        @Test
        void completedRunWithFailuresIsRedAndListsThem() {
            JsonNode body = build(WebhookFormat.SLACK, WebhookEventType.RUN_COMPLETED,
                    runData("Nightly", 182, 8), BASE_URL);
            JsonNode attachment = body.get("attachments").get(0);

            assertThat(body.get("text").asString()).startsWith("❌");
            assertThat(attachment.get("color").asString()).isEqualTo("#c62828");
            String failedTests = attachment.get("fields").get(5).get("value").asString();
            assertThat(failedTests.split("\n")).hasSize(6).startsWith("PROJ-0 Test 0").endsWith("+3 more");
        }

        @Test
        void omitsTheLinkWithoutABaseUrl() {
            JsonNode attachment = build(WebhookFormat.SLACK, WebhookEventType.RUN_COMPLETED,
                    runData("Nightly", 1, 0), "").get("attachments").get(0);

            assertThat(attachment.has("title_link")).isFalse();
        }

        @Test
        void escapesMentionsAndControlCharacters() {
            JsonNode body = build(WebhookFormat.SLACK, WebhookEventType.RUN_STARTED,
                    runData("<!channel> & @here", 0, 0), BASE_URL);

            String text = body.get("text").asString();
            assertThat(text).contains("&lt;!channel&gt; &amp; @\u200Bhere").doesNotContain("<!channel>");
        }

        @Test
        void truncatesLongNames() {
            JsonNode body = build(WebhookFormat.SLACK, WebhookEventType.RUN_STARTED,
                    runData("x".repeat(500), 0, 0), BASE_URL);

            assertThat(body.get("text").asString()).contains("x".repeat(149) + "…").doesNotContain("x".repeat(150));
        }

        @Test
        void bugReportLinksToTheBug() {
            UUID bugId = UUID.randomUUID();
            JsonNode attachment = build(WebhookFormat.SLACK, WebhookEventType.BUG_REPORT_CREATED,
                    Map.of("bugReportId", bugId.toString(), "title", "Login broken", "priority", "HIGH", "status", "OPEN"),
                    BASE_URL).get("attachments").get(0);

            assertThat(attachment.get("title").asString()).isEqualTo("🐞 New bug in PROJ: Login broken");
            assertThat(attachment.get("title_link").asString()).endsWith("/bug-reports/" + bugId);
        }

        @Test
        void testMessageNamesTheProject() {
            JsonNode body = MAPPER.readTree(builder(BASE_URL)
                    .buildTest(WebhookFormat.SLACK, WebhookEventType.RUN_COMPLETED, project));

            assertThat(body.get("text").asString()).contains("Test message from Testmanagement for project PROJ");
        }
    }

    @Nested
    class Teams {

        @Test
        void rendersAnAdaptiveCardWithFactsAndOpenAction() {
            JsonNode attachment = build(WebhookFormat.TEAMS, WebhookEventType.RUN_COMPLETED,
                    runData("Nightly", 9, 1), BASE_URL).get("attachments").get(0);
            JsonNode card = attachment.get("content");

            assertThat(attachment.get("contentType").asString()).isEqualTo("application/vnd.microsoft.card.adaptive");
            assertThat(card.get("type").asString()).isEqualTo("AdaptiveCard");
            assertThat(card.get("body").get(0).get("color").asString()).isEqualTo("Attention");
            assertThat(card.get("body").get(1).get("type").asString()).isEqualTo("FactSet");
            assertThat(card.get("actions").get(0).get("url").asString()).endsWith("/test-runs/" + RUN_ID);
        }

        @Test
        void omitsActionsWithoutABaseUrl() {
            JsonNode card = build(WebhookFormat.TEAMS, WebhookEventType.RUN_COMPLETED,
                    runData("Nightly", 1, 0), "").get("attachments").get(0).get("content");

            assertThat(card.has("actions")).isFalse();
        }

        @Test
        void escapesMarkdown() {
            JsonNode card = build(WebhookFormat.TEAMS, WebhookEventType.RUN_STARTED,
                    runData("**bold** [x](http://evil)", 0, 0), BASE_URL).get("attachments").get(0).get("content");

            assertThat(card.get("body").get(0).get("text").asString())
                    .contains("\\*\\*bold\\*\\* \\[x\\]\\(http://evil\\)");
        }
    }
}
