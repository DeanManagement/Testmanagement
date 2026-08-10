package com.deanmanagement.testmanagement.project.internal.mcp;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Collection;

import java.util.UUID;

/**
 * Records every MCP tool call (PRD-025 §3.6).
 *
 * <p>An aspect rather than a line in each tool: audit a developer has to remember to add is audit
 * that goes missing on the thirteenth tool. Keying off {@code @McpTool} means a new tool is covered
 * the moment it exists.
 *
 * <p>{@code REFUSED} is separated from {@code ERROR} so an admin reading the activity table can
 * tell "a guard stopped the agent" from "the tool broke" — the first is the system working.
 */
@Aspect
@Component
// Ordered ahead of @Transactional (LOWEST_PRECEDENCE) so this is the outer advice and sees the
// tool's transaction commit or fail rather than racing it; with both at the default the ordering
// is undefined, and the losing arrangement writes a SUCCESS row for work a later commit failure
// then rolls back.
//
// Not HIGHEST_PRECEDENCE itself: that slot belongs to Spring's ExposeInvocationInterceptor, which
// AspectJ needs in place before it can bind @annotation(mcpTool). Taking it produces
// "Required to bind 2 arguments, but only bound 1" on every single tool call.
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class McpToolAuditor {

    private static final int MAX_ARGUMENTS_CHARS = 4000;
    private static final int MAX_ERROR_CHARS = 1000;

    private final McpInvocationRecorder recorder;

    @Around("@annotation(mcpTool)")
    public Object audit(ProceedingJoinPoint joinPoint, McpTool mcpTool) throws Throwable {
        long start = System.nanoTime();
        String arguments = truncate(describeArguments(joinPoint), MAX_ARGUMENTS_CHARS);
        // Cleared on the way in as well as out: the holder is set in McpCallerContext, a different
        // class, so any future caller of require() that is not a tool could otherwise leave a
        // value behind for the next call on this pooled thread to be attributed to.
        McpCallerHolder.clear();
        try {
            Object result = joinPoint.proceed();
            Created created = describeCreated(result);
            recorder.record(McpCallerHolder.get(), mcpTool.name(), arguments, "SUCCESS", null,
                    created.type(), created.id(), elapsedMs(start));
            return result;
        } catch (McpToolException refused) {
            recorder.record(McpCallerHolder.get(), mcpTool.name(), arguments, "REFUSED",
                    truncate(refused.getMessage(), MAX_ERROR_CHARS), null, null, elapsedMs(start));
            throw refused;
        } catch (Throwable error) {
            recorder.record(McpCallerHolder.get(), mcpTool.name(), arguments, "ERROR",
                    truncate(error.getMessage(), MAX_ERROR_CHARS), null, null, elapsedMs(start));
            throw error;
        } finally {
            // Request threads are pooled; a stale caller here would misattribute the next call.
            McpCallerHolder.clear();
        }
    }

    private record Created(String type, UUID id) {
        static final Created NONE = new Created(null, null);
    }

    /** Links the audit row to what the call produced, so "what did this agent create?" is answerable. */
    private static Created describeCreated(Object result) {
        return switch (result) {
            case McpDtos.CreatedTestCase testCase -> new Created("TEST_CASE", testCase.id());
            case McpDtos.CreatedSuite suite -> new Created("TEST_SUITE", suite.id());
            case McpDtos.CreatedPlan plan -> new Created("TEST_PLAN", plan.id());
            case McpDtos.BulkResult bulk -> new Created("TEST_CASE_BULK(" + bulk.created() + ")", null);
            case null, default -> Created.NONE;
        };
    }

    /**
     * Parameter names with a <em>shape</em> for each value, not the value itself.
     *
     * <p>Free text is summarised as its length. That is deliberate: a step's {@code testData} is
     * the single field in this domain most likely to hold a test-account password or a token —
     * that is what "test data" means — and descriptions and preconditions routinely carry internal
     * hostnames. Copying them here would duplicate them out of the project-scoped
     * {@code test_cases} table into an admin-global audit table with a longer retention, which is
     * a worse place for them to live.
     *
     * <p>Enums, booleans, numbers and UUIDs are kept verbatim: they are the parts that make the
     * row useful for answering "what did this agent do?", and none of them can carry a secret.
     */
    private static String describeArguments(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return "{}";
        }
        String[] names = ((MethodSignature) joinPoint.getSignature()).getParameterNames();
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(names != null && i < names.length ? names[i] : "arg" + i)
                    .append('=')
                    .append(render(args[i]));
        }
        return sb.append('}').toString();
    }

    private static String render(Object value) {
        return switch (value) {
            case null -> "null";
            case String text -> "<" + text.length() + " chars>";
            case Collection<?> collection -> "<" + collection.size() + " items>";
            case Enum<?> constant -> constant.name();
            case Boolean bool -> bool.toString();
            case Number number -> number.toString();
            case UUID uuid -> uuid.toString();
            // Records like McpDtos.Step reach here; their fields are free text, so only the shape.
            default -> "<" + value.getClass().getSimpleName() + ">";
        };
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
