package com.deanmanagement.testmanagement.project.internal.mcp;

/**
 * A tool failure the calling agent can do something about — wrong role, unknown id, duplicate
 * title, budget exhausted.
 *
 * <p>Spring AI turns a thrown exception into an MCP tool result with {@code isError}, which is the
 * point: an agent can read that and choose a different action, whereas an HTTP 5xx only tells it
 * the server broke. Messages here are written for a model to act on, so they say what to do next,
 * not just what went wrong.
 */
public class McpToolException extends RuntimeException {

    public McpToolException(String message) {
        super(message);
    }
}
