package com.miniproject.backend.mcp;

public class McpToolException extends RuntimeException {

    public McpToolException(String toolName, String message) {
        super("MCP tool '" + toolName + "' failed: " + message);
    }

    public McpToolException(String toolName, Throwable cause) {
        super("MCP tool '" + toolName + "' failed", cause);
    }
}
