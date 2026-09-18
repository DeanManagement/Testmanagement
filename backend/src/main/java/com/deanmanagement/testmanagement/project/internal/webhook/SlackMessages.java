package com.deanmanagement.testmanagement.project.internal.webhook;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Renders a {@link ChatMessage} as a Slack incoming-webhook body. Legacy {@code attachments} rather
 * than Block Kit, because it is the one shape Slack, Mattermost and Rocket.Chat all render.
 */
final class SlackMessages {

    private static final Map<ChatMessage.Outcome, String> COLORS = Map.of(
            ChatMessage.Outcome.SUCCESS, "#2e7d32",
            ChatMessage.Outcome.FAILURE, "#c62828",
            ChatMessage.Outcome.NEUTRAL, "#f9a825");
    private static final String ZERO_WIDTH_SPACE = "\u200B";

    private SlackMessages() {
    }

    static Map<String, Object> render(ChatMessage message) {
        Map<String, Object> attachment = new LinkedHashMap<>();
        attachment.put("fallback", escape(message.headline()));
        attachment.put("color", COLORS.get(message.outcome()));
        attachment.put("title", escape(message.headline()));
        if (message.linkUrl() != null) {
            attachment.put("title_link", message.linkUrl());
        }
        List<Map<String, Object>> fields = new ArrayList<>();
        for (ChatMessage.Field field : message.fields()) {
            fields.add(Map.of("title", field.title(), "value", escape(field.value()), "short", true));
        }
        if (!message.details().isEmpty()) {
            fields.add(Map.of("title", "Failed tests",
                    "value", escape(String.join("\n", message.details())), "short", false));
        }
        if (!fields.isEmpty()) {
            attachment.put("fields", fields);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("text", escape(message.headline()));
        body.put("attachments", List.of(attachment));
        return body;
    }

    /**
     * Slack's control characters become entities, so {@code <!channel>} can't be a mention; a
     * zero-width space after {@code @} defuses Mattermost's plain {@code @channel}/{@code @all}.
     */
    static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("@", "@" + ZERO_WIDTH_SPACE);
    }
}
