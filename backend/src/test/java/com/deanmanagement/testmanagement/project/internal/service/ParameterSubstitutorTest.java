package com.deanmanagement.testmanagement.project.internal.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Placeholder substitution (PRD-015 §3.2). The judgement worth protecting is what happens to a
 * placeholder with no value: it stays visible rather than vanishing.
 */
class ParameterSubstitutorTest {

    private static Map<String, String> values(String... pairs) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], pairs[i + 1]);
        }
        return map;
    }

    @Test
    void replacesAKnownPlaceholder() {
        assertThat(ParameterSubstitutor.substitute("Log in as {username}", values("username", "alice")))
                .isEqualTo("Log in as alice");
    }

    @Test
    void replacesEveryOccurrence() {
        assertThat(ParameterSubstitutor.substitute("{a} then {a} again", values("a", "x")))
                .isEqualTo("x then x again");
    }

    @Test
    void replacesSeveralDifferentKeys() {
        assertThat(ParameterSubstitutor.substitute(
                "Transfer {amount} to {account}", values("amount", "100", "account", "ACME")))
                .isEqualTo("Transfer 100 to ACME");
    }

    @Test
    void leavesAnUnknownPlaceholderLiteral() {
        // Blanking it would produce "enter " — a step that looks finished and is quietly wrong.
        // Leaving "{password}" makes the gap obvious to whoever is following the steps.
        assertThat(ParameterSubstitutor.substitute("enter {password}", values("username", "alice")))
                .isEqualTo("enter {password}");
    }

    @Test
    void mixesResolvedAndUnresolvedInOneString() {
        assertThat(ParameterSubstitutor.substitute(
                "{user} enters {password}", values("user", "alice")))
                .isEqualTo("alice enters {password}");
    }

    @Test
    void leavesProseInBracesAlone() {
        // Braces around a sentence are not a placeholder; only identifier-shaped keys match.
        assertThat(ParameterSubstitutor.substitute("press {the enter key}", values("a", "b")))
                .isEqualTo("press {the enter key}");
    }

    @Test
    void treatsDollarAndBackslashInValuesAsLiteralText() {
        // These are regex replacement metacharacters; unescaped they would corrupt the output.
        assertThat(ParameterSubstitutor.substitute("pay {amount}", values("amount", "$5")))
                .isEqualTo("pay $5");
        assertThat(ParameterSubstitutor.substitute("path {p}", values("p", "C:\\temp")))
                .isEqualTo("path C:\\temp");
    }

    @Test
    void handlesNullAndEmptyInputsWithoutBlowingUp() {
        assertThat(ParameterSubstitutor.substitute(null, values("a", "b"))).isNull();
        assertThat(ParameterSubstitutor.substitute("", values("a", "b"))).isEmpty();
        assertThat(ParameterSubstitutor.substitute("plain text", Map.of())).isEqualTo("plain text");
        assertThat(ParameterSubstitutor.substitute("plain {a}", null)).isEqualTo("plain {a}");
    }

    @Test
    void reportsPlaceholdersInOrderOfAppearance() {
        assertThat(ParameterSubstitutor.placeholdersIn("{b} then {a} then {b}"))
                .containsExactly("b", "a");
    }

    @Test
    void reportsOnlyTheUnfillablePlaceholders() {
        assertThat(ParameterSubstitutor.unresolvedIn(
                "{user} enters {password}", values("user", "alice")))
                .containsExactly("password");
    }

    @Test
    void acceptsDotDashAndUnderscoreInKeys() {
        assertThat(ParameterSubstitutor.substitute(
                "{user.name}-{item_id}-{a-b}", values("user.name", "a", "item_id", "1", "a-b", "z")))
                .isEqualTo("a-1-z");
    }
}
