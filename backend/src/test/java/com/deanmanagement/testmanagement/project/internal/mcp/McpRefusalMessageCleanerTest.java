package com.deanmanagement.testmanagement.project.internal.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PRD-027 §8.2 — the trimming rules, away from a Spring context.
 *
 * <p>The end-to-end assertion that the framing is gone from a real {@code tools/call} lives in
 * {@code McpEndpointApiTest}; this covers the cases that are awkward to provoke over the protocol.
 */
class McpRefusalMessageCleanerTest {

    private static McpSchema.CallToolResult errorWith(String text) {
        return new McpSchema.CallToolResult(List.of(new McpSchema.TextContent(text)), true, null,
                null);
    }

    private static String textOf(McpSchema.CallToolResult result) {
        return ((McpSchema.TextContent) result.content().getFirst()).text();
    }

    @Test
    void theFramingLineIsRemovedAndTheRefusalSurvivesIntact() {
        McpSchema.CallToolResult cleaned = McpRefusalMessageCleaner.clean(errorWith("Error invoking method: recordTestResult\n"
                        + "Pass either testCaseId or resultId."));

        assertThat(textOf(cleaned)).isEqualTo("Pass either testCaseId or resultId.");
    }

    @Test
    void aMultiLineRefusalKeepsAllOfItsLines() {
        McpSchema.CallToolResult cleaned = McpRefusalMessageCleaner.clean(errorWith("Error invoking method: createBugReport\nAn open bug already has this "
                        + "title: abc (OPEN) Boom.\nUse change_bug_report_status on it."));

        assertThat(textOf(cleaned))
                .startsWith("An open bug already has this title")
                .contains("change_bug_report_status")
                .doesNotContain("Error invoking method");
    }

    /**
     * A message-less exception would otherwise leave the literal string "null" as the entire
     * user-facing text, which is worse than the framing it replaced.
     */
    @Test
    void framingWithNoMessageBehindItBecomesSomethingReadable() {
        assertThat(textOf(McpRefusalMessageCleaner.clean(errorWith("Error invoking method: getTestRun\nnull"))))
                .isEqualTo(McpRefusalMessageCleaner.NO_MESSAGE);

        assertThat(textOf(McpRefusalMessageCleaner.clean(errorWith("Error invoking method: getTestRun"))))
                .isEqualTo(McpRefusalMessageCleaner.NO_MESSAGE);
    }

    /**
     * The framing names the <em>Java method</em> ({@code createTestCase}), not the MCP tool
     * ({@code create_test_case}) — the first version of this class checked the text against the
     * tool name and so never matched anything, and every unit test here passed because they were
     * written with the same wrong assumption. It was the end-to-end assertion that caught it.
     */
    @Test
    void theMethodNameInTheFramingNeedNotMatchTheToolName() {
        McpSchema.CallToolResult cleaned = McpRefusalMessageCleaner.clean(
                errorWith("Error invoking method: createTestCase\n"
                        + "Invalid arguments — title: must not be blank"));

        assertThat(textOf(cleaned)).isEqualTo("Invalid arguments — title: must not be blank");
    }

    /**
     * The guard that replaced name-matching: the framing always names a bare identifier, so a
     * message that starts with the same words and then reads as prose is somebody else's and is
     * left alone.
     */
    @Test
    void aMessageThatOnlyLooksLikeFramingIsLeftAlone() {
        String text = "Error invoking method: the upstream service refused\nTry later.";

        assertThat(textOf(McpRefusalMessageCleaner.clean(errorWith(text)))).isEqualTo(text);
    }

    @Test
    void aSuccessfulResultIsNotTouched() {
        McpSchema.CallToolResult success = new McpSchema.CallToolResult(
                List.of(new McpSchema.TextContent("Error invoking method: x\nlooks like framing")),
                false, null, null);

        assertThat(McpRefusalMessageCleaner.clean(success)).isSameAs(success);
    }

    @Test
    void anErrorWithoutTheFramingIsNotTouched() {
        assertThat(textOf(McpRefusalMessageCleaner.clean(errorWith("Write budget exhausted: at most 120 writes per minute."))))
                .isEqualTo("Write budget exhausted: at most 120 writes per minute.");
    }

    @Test
    void structuredContentAndTheErrorFlagSurviveTheRewrite() {
        McpSchema.CallToolResult cleaned = McpRefusalMessageCleaner.clean(new McpSchema.CallToolResult(
                        List.of(new McpSchema.TextContent("Error invoking method: t\nnope")),
                        true, "kept", null));

        assertThat(cleaned.isError()).isTrue();
        assertThat(cleaned.structuredContent()).isEqualTo("kept");
    }
}
