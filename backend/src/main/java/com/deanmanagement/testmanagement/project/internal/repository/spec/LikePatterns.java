package com.deanmanagement.testmanagement.project.internal.repository.spec;

import java.util.Locale;

/**
 * Search text as a case-insensitive "contains" LIKE pattern (TES-BUG-21). The text is matched
 * literally: {@code %} and {@code _} typed by a user are escaped, so they no longer match everything.
 * Every LIKE using a pattern from here must declare {@link #ESCAPE}; {@code !} rather than a
 * backslash, which HQL, H2 and PostgreSQL would each read differently inside a string literal.
 */
public final class LikePatterns {

    public static final char ESCAPE = '!';

    private LikePatterns() {
    }

    /** Matches values that contain {@code text}, compared in lower case. */
    public static String containing(String text) {
        String escaped = text.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }
}
