package com.deanmanagement.testmanagement.project.internal.mcp;

import jakarta.servlet.Filter;

/**
 * The MCP module's public surface for the security chain, which lives in a sibling package and
 * cannot see package-private filters directly.
 */
public final class McpFilters {

    private static final String MCP_ENDPOINT = "/api/mcp";

    /** @see McpAcceptHeaderFilter */
    public static Filter acceptHeader() {
        return new McpAcceptHeaderFilter(MCP_ENDPOINT);
    }

    /** @see McpToolListFilter */
    public static Filter toolList(McpCallerContext callerContext, McpToolGroups toolGroups) {
        return new McpToolListFilter(MCP_ENDPOINT, callerContext, toolGroups);
    }

    private McpFilters() {
    }
}
