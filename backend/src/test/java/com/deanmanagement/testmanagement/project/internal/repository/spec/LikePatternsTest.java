package com.deanmanagement.testmanagement.project.internal.repository.spec;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** TES-BUG-21: search text is matched literally, whatever it contains. */
class LikePatternsTest {

    @Test
    void wildcardsAndTheEscapeCharacterAreEscaped() {
        assertThat(LikePatterns.containing("100%_off!")).isEqualTo("%100!%!_off!!%");
    }

    @Test
    void theTextIsComparedInLowerCase() {
        assertThat(LikePatterns.containing("Login")).isEqualTo("%login%");
    }

    @Test
    void emptyTextMatchesEverything() {
        assertThat(LikePatterns.containing("")).isEqualTo("%%");
    }
}
