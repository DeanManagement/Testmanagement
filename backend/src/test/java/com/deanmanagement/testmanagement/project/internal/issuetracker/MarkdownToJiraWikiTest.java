package com.deanmanagement.testmanagement.project.internal.issuetracker;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers what {@code IssueLinkService.buildBody} emits, plus the two constructs PRD-029 names. */
class MarkdownToJiraWikiTest {

    @Test
    void boldBecomesSingleAsterisks() {
        assertThat(MarkdownToJiraWiki.convert("**Test case:** WEB-1 — Login"))
                .isEqualTo("*Test case:* WEB-1 — Login");
    }

    @Test
    void aLinkBecomesTextPipeUrl() {
        assertThat(MarkdownToJiraWiki.convert("See [the run](https://tm.example/runs/7) for details"))
                .isEqualTo("See [the run|https://tm.example/runs/7] for details");
    }

    @Test
    void aFencedBlockBecomesACodeMacroAndItsContentIsLeftAlone() {
        String markdown = "Log:\n```\n**not bold** [not](a-link)\n```\ndone";

        assertThat(MarkdownToJiraWiki.convert(markdown))
                .isEqualTo("Log:\n{code}\n**not bold** [not](a-link)\n{code}\ndone");
    }

    @Test
    void aLanguageHintOnTheFenceIsDropped() {
        assertThat(MarkdownToJiraWiki.convert("```json\n{}\n```")).isEqualTo("{code}\n{}\n{code}");
    }

    @Test
    void bulletsAndPlainTextPassThrough() {
        String body = "**Actual results:**\n- FAILED: HTTP 500 on submit\n- BLOCKED: could not continue\n";

        // "- item" is a list in Jira wiki markup as well, so the bullets need no translation.
        assertThat(MarkdownToJiraWiki.convert(body))
                .isEqualTo("*Actual results:*\n- FAILED: HTTP 500 on submit\n- BLOCKED: could not continue\n");
    }

    @Test
    void theWholeTemplatedBodySurvives() {
        String body = """
                **Test case:** WEB-1 — Login
                **Result:** FAILED

                **Tester comment:**
                Reproduced twice
                """;

        assertThat(MarkdownToJiraWiki.convert(body)).isEqualTo("""
                *Test case:* WEB-1 — Login
                *Result:* FAILED

                *Tester comment:*
                Reproduced twice
                """);
    }

    @Test
    void nothingToConvertIsReturnedUnchanged() {
        assertThat(MarkdownToJiraWiki.convert("")).isEmpty();
        assertThat(MarkdownToJiraWiki.convert(null)).isNull();
    }
}
