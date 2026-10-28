package com.deanmanagement.testmanagement.project.internal.issuetracker;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the HTML a tracker stores rich text as into plain text with Markdown-ish line structure
 * (TM19): paragraphs and line breaks become newlines, list items become {@code - } lines, every
 * other tag is dropped and entities are decoded. Nothing of the markup survives, so nothing of it
 * is stored; a literal {@code <} written as text in the tracker stays text, and the description is
 * rendered by the Markdown pipe, which shows raw HTML as text anyway.
 *
 * <p>Deliberately not an HTML parser: malformed markup degrades to some leftover text, never to
 * markup.
 */
public final class HtmlToText {

    private static final Pattern DROPPED_ELEMENTS =
            Pattern.compile("(?is)<(script|style|head)\\b[^>]*>.*?</\\1\\s*>");
    private static final Pattern COMMENTS = Pattern.compile("(?s)<!--.*?-->");
    private static final Pattern LINE_BREAK = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern LIST_ITEM = Pattern.compile("(?i)<li\\b[^>]*>");
    private static final Pattern BLOCK_END =
            Pattern.compile("(?i)</?(p|div|h[1-6]|ul|ol|tr|table|blockquote|pre)\\b[^>]*>");
    private static final Pattern ANY_TAG = Pattern.compile("<[a-zA-Z/!?][^>]*>");
    private static final Pattern ENTITY = Pattern.compile("&(#[0-9]{1,7}|#[xX][0-9a-fA-F]{1,6}|[a-zA-Z]{2,8});");
    private static final Pattern SPACES = Pattern.compile("[ \\t\\x0B\\f\\u00A0]+");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");

    private HtmlToText() {
    }

    public static String convert(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        String text = DROPPED_ELEMENTS.matcher(html).replaceAll("");
        text = COMMENTS.matcher(text).replaceAll("");
        text = text.replace("\r\n", "\n").replace('\r', '\n').replace('\n', ' ');
        text = LINE_BREAK.matcher(text).replaceAll("\n");
        text = LIST_ITEM.matcher(text).replaceAll("\n- ");
        text = BLOCK_END.matcher(text).replaceAll("\n");
        text = ANY_TAG.matcher(text).replaceAll("");
        text = decodeEntities(text);
        StringBuilder lines = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            lines.append(SPACES.matcher(line).replaceAll(" ").strip()).append('\n');
        }
        String result = BLANK_LINES.matcher(lines.toString()).replaceAll("\n\n").strip();
        return result.isEmpty() ? null : result;
    }

    /** One pass, so {@code &amp;lt;} becomes the text {@code &lt;} and not {@code <}. */
    private static String decodeEntities(String text) {
        Matcher matcher = ENTITY.matcher(text);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(out, Matcher.quoteReplacement(decode(matcher.group(1), matcher.group())));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String decode(String entity, String original) {
        if (entity.startsWith("#")) {
            boolean hex = entity.length() > 1 && (entity.charAt(1) == 'x' || entity.charAt(1) == 'X');
            int codePoint = Integer.parseInt(entity.substring(hex ? 2 : 1), hex ? 16 : 10);
            return Character.isValidCodePoint(codePoint) && codePoint != 0 ? Character.toString(codePoint) : "";
        }
        return switch (entity) {
            case "amp" -> "&";
            case "lt" -> "<";
            case "gt" -> ">";
            case "quot" -> "\"";
            case "apos" -> "'";
            case "nbsp" -> " ";
            default -> original;
        };
    }
}
