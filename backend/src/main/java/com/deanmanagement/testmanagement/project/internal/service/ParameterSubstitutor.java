package com.deanmanagement.testmanagement.project.internal.service;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces {@code {key}} placeholders in step text with a parameter set's values (PRD-015 §3.2).
 *
 * <p>Substitution happens at read time, not write time. The stored step text stays templated and
 * editable, and a result keeps the values it executed with, so an old result renders exactly as it
 * did even after the template changes.
 *
 * <p>An unknown placeholder is left <em>literal</em> rather than blanked. A step reading "enter
 * {username}" is obviously unfinished; a step reading "enter " looks complete and is quietly wrong,
 * which is the more dangerous failure in a document someone follows by hand.
 */
public final class ParameterSubstitutor {

    /** Keys are identifier-ish on purpose: braces in prose ("press {enter}") should not match. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z0-9_.-]+)}");

    private ParameterSubstitutor() {
    }

    public static String substitute(String text, Map<String, String> values) {
        if (text == null || text.isEmpty() || values == null || values.isEmpty()) {
            return text;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = values.get(key);
            // No match: keep the placeholder verbatim so the gap is visible.
            matcher.appendReplacement(result,
                    Matcher.quoteReplacement(replacement != null ? replacement : matcher.group(0)));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /** Placeholders present in the text, in order of first appearance. */
    public static Set<String> placeholdersIn(String text) {
        Set<String> keys = new LinkedHashSet<>();
        if (text == null || text.isEmpty()) {
            return keys;
        }
        Matcher matcher = PLACEHOLDER.matcher(text);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    /** Placeholders the given values cannot fill — what the editor flags as unresolved. */
    public static Set<String> unresolvedIn(String text, Map<String, String> values) {
        Set<String> unresolved = new LinkedHashSet<>(placeholdersIn(text));
        if (values != null) {
            unresolved.removeAll(values.keySet());
        }
        return unresolved;
    }
}
