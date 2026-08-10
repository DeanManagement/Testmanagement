package com.deanmanagement.testmanagement.project.internal.mcp;

/**
 * Carries the caller resolved by {@link McpCallerContext} to {@link McpToolAuditor}, which runs
 * around the same call but has no access to its arguments' meaning.
 *
 * <p>The alternative — having the auditor look the key up itself — was tried and is worse on two
 * counts. It adds a second key query to every single tool call, and the audit row is written in a
 * {@code REQUIRES_NEW} transaction, so that lookup cannot see anything the current transaction has
 * not committed. Reusing what the tool already resolved avoids both.
 *
 * <p>Cleared by the auditor on both entry and exit of every {@code @McpTool} method, so a pooled
 * request thread never inherits a previous call's caller.
 *
 * <p><strong>Read only by the auditor.</strong> Nothing authorizes off this — {@link
 * McpCallerContext#require()} always re-resolves from the {@code SecurityContextHolder}. Keep it
 * that way: the worst a stale value here can cause is a misattributed audit row, and treating it
 * as an authorization source would turn that into a cross-tenant bug.
 */
final class McpCallerHolder {

    private static final ThreadLocal<McpCallerContext.Caller> CURRENT = new ThreadLocal<>();

    private McpCallerHolder() {
    }

    static void set(McpCallerContext.Caller caller) {
        CURRENT.set(caller);
    }

    static McpCallerContext.Caller get() {
        return CURRENT.get();
    }

    static void clear() {
        CURRENT.remove();
    }
}
