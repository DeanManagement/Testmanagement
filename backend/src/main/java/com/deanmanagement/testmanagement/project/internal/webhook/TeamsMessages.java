package com.deanmanagement.testmanagement.project.internal.webhook;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Renders a {@link ChatMessage} as an Adaptive Card for a Teams Workflows "When a Teams webhook
 * request is received" trigger. The retired Office 365 connector {@code MessageCard} isn't supported.
 */
final class TeamsMessages {

    private static final Map<ChatMessage.Outcome, String> COLORS = Map.of(
            ChatMessage.Outcome.SUCCESS, "Good",
            ChatMessage.Outcome.FAILURE, "Attention",
            ChatMessage.Outcome.NEUTRAL, "Warning");
    private static final Pattern MARKDOWN_CHARS = Pattern.compile("([\\\\*_\\[\\]()~`#>])");

    private TeamsMessages() {
    }

    static Map<String, Object> render(ChatMessage message) {
        List<Map<String, Object>> cardBody = new ArrayList<>();
        cardBody.add(Map.of("type", "TextBlock", "text", escape(message.headline()), "weight", "Bolder",
                "size", "Medium", "wrap", true, "color", COLORS.get(message.outcome())));
        if (!message.fields().isEmpty()) {
            List<Map<String, Object>> facts = message.fields().stream()
                    .map(f -> Map.<String, Object>of("title", f.title(), "value", escape(f.value())))
                    .toList();
            cardBody.add(Map.of("type", "FactSet", "facts", facts));
        }
        if (!message.details().isEmpty()) {
            cardBody.add(Map.of("type", "TextBlock", "text", "Failed tests", "weight", "Bolder", "wrap", true));
            for (String line : message.details()) {
                cardBody.add(Map.of("type", "TextBlock", "text", escape(line), "wrap", true, "spacing", "None"));
            }
        }
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("$schema", "http://adaptivecards.io/schemas/adaptive-card.json");
        card.put("type", "AdaptiveCard");
        card.put("version", "1.4");
        card.put("body", cardBody);
        if (message.linkUrl() != null) {
            card.put("actions", List.of(Map.of("type", "Action.OpenUrl", "title", message.linkLabel(),
                    "url", message.linkUrl())));
        }
        return Map.of("type", "message", "attachments", List.of(Map.of(
                "contentType", "application/vnd.microsoft.card.adaptive", "content", card)));
    }

    /** Backslash-escapes the markdown subset Adaptive Card TextBlocks and facts interpret. */
    static String escape(String text) {
        return MARKDOWN_CHARS.matcher(text).replaceAll("\\\\$1");
    }
}
