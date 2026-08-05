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

import java.time.Duration;
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
                // SDK default request timeout (~20s) is fine for lookups but far too
                // short for index_project on a large repo (e.g. an 80k+ node external
                // codebase takes several minutes) — surfaced as a generic
                // McpToolException wrapping a timeout, easy to mistake for a real
                // indexing failure. All tool calls share one client, so this applies
                // to every call, not just indexing.
                .requestTimeout(Duration.ofMinutes(15))
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

    @Override
    public Map<String, Object> searchProjectContext(String project, String query, int limit) {
        return callTool("search_project_context", Map.of("project", project, "query", query, "limit", limit));
    }

    @Override
    public Map<String, Object> indexProject(String repoPath, String name) {
        return callTool("index_project", Map.of("repo_path", repoPath, "name", name));
    }

    @Override
    public Map<String, Object> projectIndexStatus(String project) {
        return callTool("project_index_status", Map.of("project", project));
    }

    @Override
    public Map<String, Object> getArchitecture(String project, List<String> aspects) {
        return callTool("get_architecture", Map.<String, Object>of("project", project, "aspects", aspects));
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
