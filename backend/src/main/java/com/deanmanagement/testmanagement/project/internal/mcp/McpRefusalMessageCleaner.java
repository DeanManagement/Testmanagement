package com.deanmanagement.testmanagement.project.internal.mcp;

import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Strips the framing Spring AI puts in front of a tool refusal.
 *
 * <p>PRD-025 §3.6 draws a line between errors an agent can act on and errors it can only give up
 * on, and returns the former as tool results with {@code isError} rather than HTTP 5xx. That works.
 * What did not work is how they read. Every {@link McpToolException} — a deliberate, actionable
 * refusal with a message telling the agent exactly what to do instead — arrived like this:
 *
 * <pre>
 * Error invoking method: recordTestResult
 * Pass either testCaseId or resultId.
 * </pre>
 *
 * <p>The second line is ours. The first is Spring AI's, and it actively misleads: "Error invoking
 * method" reads as "this tool is broken", which is the one conclusion we do not want. A capable
 * model looks past it. A smaller local model — already the kind inclined to work around a tool
 * rather than fix its own call — may reasonably decide the tool is faulty and stop using it. The
 * agent already knows which tool it called, so the line carries no information either.
 *
 * <p>{@code AbstractSyncMcpToolMethodCallback.createSyncErrorResult} builds the text as
 * {@code e.getMessage() + lineSeparator() + cause.getMessage()}, and
 * {@code createErrorMessage} is {@code "Error invoking method: %s"}. It is {@code protected}, so
 * overriding it looks like the answer — but {@code SyncMcpToolMethodCallback} is {@code final}, so
 * there is nothing to subclass.
 *
 * <p>What is reachable is the specification. The stateless server takes its tools as
 * {@code List<SyncToolSpecification>} beans, and a specification is a record of a
 * {@link McpSchema.Tool} and a call handler — so the handler can be wrapped without touching the
 * annotations, and the schema generation PRD-025 §3.1 valued stays exactly as it was.
 *
 * <p><b>Why this only trims, and does not try to classify.</b> By the time the result exists the
 * exception is gone, so a genuine internal failure and a deliberate refusal look identical here.
 * Trimming is right for both: for a refusal it leaves the instruction, and for a real fault it
 * leaves the underlying message, which is more use to a reader than the name of a method they
 * already know. {@code isError} still says it failed, and {@code mcp_tool_invocations} still
 * records REFUSED against ERROR for anyone who needs the distinction.
 */
@Configuration
@ConditionalOnProperty(name = "app.mcp.enabled", havingValue = "true")
public class McpRefusalMessageCleaner {

    static final String PREFIX = "Error invoking method: ";

    /**
     * Left when the framing is all there was — better than the literal "null" that a
     * message-less exception would otherwise produce.
     */
    static final String NO_MESSAGE = "The tool failed without reporting a reason.";

    /**
     * {@code static} so it is instantiated before the beans it post-processes. A non-static
     * {@code BeanPostProcessor} factory method forces its configuration class to be created early,
     * which takes every bean that class depends on out of the reach of other post-processors.
     *
     * <p>Named for what it does rather than after the class: a {@code @Bean} method sharing its
     * configuration class's name collides with the component-scanned definition and the context
     * refuses to start.
     */
    @Bean
    static BeanPostProcessor stripSpringAiToolErrorFraming() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName)
                    throws BeansException {
                if (!(bean instanceof List<?> list) || list.isEmpty()
                        || !(list.getFirst() instanceof SyncToolSpecification)) {
                    return bean;
                }
                return list.stream()
                        .map(SyncToolSpecification.class::cast)
                        .map(McpRefusalMessageCleaner::wrap)
                        .toList();
            }
        };
    }

    private static SyncToolSpecification wrap(SyncToolSpecification specification) {
        var delegate = specification.callHandler();
        return SyncToolSpecification.builder()
                .tool(specification.tool())
                .callHandler((context, request) -> clean(delegate.apply(context, request)))
                .build();
    }

    /** Removes the {@code Error invoking method: <name>} line from a failed result. */
    static McpSchema.CallToolResult clean(McpSchema.CallToolResult result) {
        if (result == null || !Boolean.TRUE.equals(result.isError()) || result.content() == null) {
            return result;
        }

        List<McpSchema.Content> content = result.content().stream()
                .map(item -> item instanceof McpSchema.TextContent text
                        ? new McpSchema.TextContent(text.annotations(), trim(text.text()),
                                text.meta())
                        : item)
                .toList();

        return new McpSchema.CallToolResult(content, result.isError(), result.structuredContent(),
                result.meta());
    }

    /**
     * The name in the framing is the <em>Java method</em> ({@code createTestCase}), not the MCP
     * tool ({@code create_test_case}), and the specification only carries the latter — so this
     * cannot check the two against each other, and an earlier version that tried silently never
     * matched anything.
     *
     * <p>What it checks instead is that the framing names a bare identifier. Spring AI's line is
     * always the prefix plus a method name; a message that merely starts with the same words and
     * then reads as prose belongs to somebody else and is left alone.
     */
    private static String trim(String text) {
        if (text == null || !text.startsWith(PREFIX)) {
            return text;
        }
        int newline = text.indexOf('\n');
        String named = (newline < 0 ? text : text.substring(0, newline))
                .substring(PREFIX.length()).strip();
        if (!named.matches("[A-Za-z0-9_$.]+")) {
            return text;
        }
        String remainder = newline < 0 ? "" : text.substring(newline + 1).stripLeading();
        return remainder.isBlank() || "null".equals(remainder) ? NO_MESSAGE : remainder;
    }
}
