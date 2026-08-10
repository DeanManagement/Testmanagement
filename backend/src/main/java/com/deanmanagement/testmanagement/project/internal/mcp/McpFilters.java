package com.deanmanagement.testmanagement.project.internal.mcp;

import jakarta.servlet.Filter;

/**
 * The MCP module's public surface for the security chain, which lives in a sibling package and
 * cannot see package-private filters directly.
 */
public final class McpFilters {

    /** @see McpAcceptHeaderFilter */
    public static Filter acceptHeader() {
        return new McpAcceptHeaderFilter("/api/mcp");
    }

    private McpFilters() {
    }
}
