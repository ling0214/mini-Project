package com.miniproject.backend.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

/**
 * MCP client wired to the Python mcp-server over stdio (see
 * docs/architecture.md - "MCP tool layer"). Spawns
 * `<pythonExecutable> -m mcp_server.server` as a subprocess on startup and
 * talks MCP over its stdin/stdout for the lifetime of this bean.
 */
@Component
public class McpToolClient implements ProjectGraphClient, AutoCloseable {

    private final McpSyncClient client;
    private final ObjectMapper json = new ObjectMapper();

    public McpToolClient(
            @Value("${mcp.python-executable:../mcp-server/.venv/Scripts/python.exe}") String pythonExecutable,
            @Value("${mcp.server-args:-m,mcp_server.server}") List<String> serverArgs) {
        ServerParameters params = ServerParameters.builder(pythonExecutable)
                .args(serverArgs)
                .build();
        StdioClientTransport transport = new StdioClientTransport(
                params,
                new JacksonMcpJsonMapper(JsonMapper.builder().build()));

        this.client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("mini-project-backend", "0.1.0"))
                .build();
        this.client.initialize();
    }

    @Override
    public Map<String, Object> getEndpointInfo(String name) {
        return callTool("get_endpoint_info", Map.of("name", name));
    }

    @Override
    public Map<String, Object> searchIssues(String query) {
        return callTool("search_issues", Map.of("query", query));
    }

    @Override
    public Map<String, Object> traceImpact(String name, int maxHops) {
        return callTool("trace_impact", Map.of("name", name, "max_hops", maxHops));
    }

    @Override
    public Map<String, Object> getTestCoverage(String name) {
        return callTool("get_test_coverage", Map.of("name", name));
    }

    private Map<String, Object> callTool(String toolName, Map<String, Object> arguments) {
        McpSchema.CallToolResult result;
        try {
            result = client.callTool(new McpSchema.CallToolRequest(toolName, arguments));
        } catch (RuntimeException e) {
            throw new McpToolException(toolName, e);
        }
        if (Boolean.TRUE.equals(result.isError())) {
            throw new McpToolException(toolName, extractText(result));
        }
        if (result.structuredContent() instanceof Map<?, ?> structured) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) structured;
            return typed;
        }
        String text = extractText(result);
        try {
            return json.readValue(text, Map.class);
        } catch (Exception e) {
            throw new McpToolException(toolName, "could not parse tool result as JSON: " + text);
        }
    }

    private String extractText(McpSchema.CallToolResult result) {
        return result.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(McpSchema.TextContent.class::cast)
                .map(McpSchema.TextContent::text)
                .findFirst()
                .orElse("");
    }

    @Override
    @PreDestroy
    public void close() {
        client.closeGracefully();
    }
}
