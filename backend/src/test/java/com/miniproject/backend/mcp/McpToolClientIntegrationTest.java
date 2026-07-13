package com.miniproject.backend.mcp;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spawns the real Python mcp-server (see mcp-server/README.md) and talks to
 * it over actual MCP-over-stdio — no mocks. This is the test that proves
 * the Java harness and the Python graph server genuinely interoperate, not
 * just that each compiles in isolation.
 */
class McpToolClientIntegrationTest {

    private static McpToolClient client;

    @BeforeAll
    static void startClient() {
        client = new McpToolClient(
                "../mcp-server/.venv/Scripts/python.exe",
                List.of("-m", "mcp_server.server"));
    }

    @AfterAll
    static void stopClient() {
        client.close();
    }

    @Test
    @SuppressWarnings("unchecked")
    void getEndpointInfoResolvesRealCallGraphFromSampleTarget() {
        Map<String, Object> info = client.getEndpointInfo("charge_card");

        assertThat(info.get("found")).isEqualTo(true);
        assertThat(info.get("file")).isEqualTo("payments.py");
        assertThat((List<Object>) info.get("called_by")).contains("checkout_endpoint");
        assertThat((List<Object>) info.get("calls")).contains("_validate_token", "_submit_to_gateway");
    }

    @Test
    void getEndpointInfoReportsNotFoundForUnknownName() {
        Map<String, Object> info = client.getEndpointInfo("does_not_exist_anywhere");

        assertThat(info.get("found")).isEqualTo(false);
    }

    @Test
    void searchIssuesFindsTheGatewayTimeoutIssue() {
        Map<String, Object> result = client.searchIssues("gateway timeout");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matches = (List<Map<String, Object>>) result.get("matches");
        assertThat(matches).anySatisfy(issue -> assertThat(issue.get("id")).isEqualTo(108));
    }
}
