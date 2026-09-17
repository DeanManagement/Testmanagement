package com.deanmanagement.testmanagement.project.internal.issuetracker;

import java.util.regex.Pattern;

/**
 * Turns the Markdown issue body into Jira wiki markup (PRD-029 §3.2). Jira does not render
 * Markdown, so a body sent as-is arrives littered with asterisks.
 *
 * <p>Deliberately not a Markdown converter. It handles what {@code IssueLinkService.buildBody}
 * emits — bold — and the two constructs a tester is likely to type into the body by hand, links and
 * fenced code. Everything else passes through as the plain text it already is. Bullets need
 * nothing: {@code - item} is a list in wiki markup too.
 */
final class MarkdownToJiraWiki {

    private static final String FENCE = "```";
    private static final String CODE_MACRO = "{code}";
    private static final Pattern BOLD = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\(([^)\\s]+)\\)");

    private MarkdownToJiraWiki() {
    }

    static String convert(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return markdown;
        }
        StringBuilder wiki = new StringBuilder(markdown.length());
        boolean insideCode = false;
        // -1 keeps trailing empty strings, so a body ending in a newline still ends in one.
        String[] lines = markdown.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            if (line.strip().startsWith(FENCE)) {
                // Whatever follows the fence is a language hint; the macro is written without one.
                insideCode = !insideCode;
                wiki.append(CODE_MACRO);
            } else {
                // Inside a code block the text is literal: asterisks there are not emphasis.
                wiki.append(insideCode ? line : convertInline(line));
            }
            if (index < lines.length - 1) {
                wiki.append('\n');
            }
        }
        return wiki.toString();
    }

    private static String convertInline(String line) {
        String withBold = BOLD.matcher(line).replaceAll("*$1*");
        return LINK.matcher(withBold).replaceAll("[$1|$2]");
    }
}
