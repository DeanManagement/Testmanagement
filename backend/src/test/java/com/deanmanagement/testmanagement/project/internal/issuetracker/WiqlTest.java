package com.deanmanagement.testmanagement.project.internal.issuetracker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PRD-026 §3.3: WIQL has no parameter binding, so the search text must never leave its string
 * literal, whatever it contains.
 */
class WiqlTest {

    @ParameterizedTest
    @ValueSource(strings = {"'; --", "' OR '1'='1", "x') OR ([System.Id] > 0", "''''", "a'b"})
    void quotesInTheTextCannotEndTheLiteral(String hostile) {
        String literal = Wiql.literal(hostile);

        String inner = literal.substring(1, literal.length() - 1);
        assertThat(literal).startsWith("'").endsWith("'");
        // Every quote inside is doubled, so none of them closes the literal.
        assertThat(inner.replace("''", "")).doesNotContain("'");
        assertThat(inner.replace("''", "'")).isEqualTo(hostile.strip());
    }

    @Test
    void controlCharactersAreDropped() {
        String withControls = "line" + (char) 10 + "break" + (char) 0;

        assertThat(Wiql.literal(withControls)).isEqualTo("'line break'");
    }

    @Test
    void theTextIsCutBeforeEscapingSoNoQuoteIsSplit() {
        String literal = Wiql.literal("a".repeat(Wiql.MAX_QUERY_LENGTH - 1) + "'''");

        assertThat(literal).isEqualTo("'" + "a".repeat(Wiql.MAX_QUERY_LENGTH - 1) + "'''");
    }

    @Test
    void aNumberAlsoMatchesTheWorkItemId() {
        assertThat(Wiql.search("Bug", " 1234 ")).contains("[System.Title] CONTAINS '1234' OR [System.Id] = 1234");
    }

    @Test
    void textIsOnlyEverATitleSearch() {
        String query = Wiql.search("Bug", "' OR [System.Id] > 0 OR '");

        assertThat(query).contains("CONTAINS ''' OR [System.Id] > 0 OR '''");
    }
}
