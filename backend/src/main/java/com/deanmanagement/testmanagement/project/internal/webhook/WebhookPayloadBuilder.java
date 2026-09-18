package com.deanmanagement.testmanagement.project.internal.webhook;

import com.deanmanagement.testmanagement.project.internal.entity.Project;
import com.deanmanagement.testmanagement.project.internal.entity.WebhookEventType;
import com.deanmanagement.testmanagement.project.internal.entity.WebhookFormat;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Serializes a webhook event into the body for a hook's {@link WebhookFormat} (PRD-031). */
@Component
public class WebhookPayloadBuilder {

    private final ObjectMapper objectMapper;
    private final String publicBaseUrl;

    /** {@code app.buildserver.public-base-url} (PRD-024) is honoured as a fallback alias. */
    public WebhookPayloadBuilder(ObjectMapper objectMapper,
                                 @Value("${app.public-base-url:}") String publicBaseUrl,
                                 @Value("${app.buildserver.public-base-url:}") String legacyPublicBaseUrl) {
        this.objectMapper = objectMapper;
        this.publicBaseUrl = publicBaseUrl.isBlank() ? legacyPublicBaseUrl : publicBaseUrl;
    }

    public String build(WebhookFormat format, WebhookEventType type, Project project, Map<String, Object> data) {
        Map<String, Object> safeData = data != null ? data : Map.of();
        return switch (format) {
            case GENERIC -> generic(type, project, safeData);
            case SLACK -> write(SlackMessages.render(ChatMessage.of(type, project, safeData, publicBaseUrl)));
            case TEAMS -> write(TeamsMessages.render(ChatMessage.of(type, project, safeData, publicBaseUrl)));
        };
    }

    public String buildTest(WebhookFormat format, WebhookEventType sampleEvent, Project project) {
        return switch (format) {
            case GENERIC -> generic(sampleEvent, project, genericTestData());
            case SLACK -> write(SlackMessages.render(ChatMessage.forTest(project)));
            case TEAMS -> write(TeamsMessages.render(ChatMessage.forTest(project)));
        };
    }

    private static Map<String, Object> genericTestData() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("test", true);
        data.put("message", "This is a test delivery from Testmanagement.");
        return data;
    }

    private String generic(WebhookEventType type, Project project, Map<String, Object> data) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", type.name());
        payload.put("projectId", project.getId().toString());
        payload.put("projectKey", project.getKey());
        payload.put("projectName", project.getName());
        payload.put("timestamp", Instant.now().toString());
        payload.put("data", data);
        return write(payload);
    }

    private String write(Map<String, Object> body) {
        return objectMapper.writeValueAsString(body);
    }
}
