package com.miniproject.backend.skills;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalLlmAnalysisClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = mock(HttpClient.class);

    @Test
    void sendsChatCompletionsRequestToConfiguredBaseUrlAndParsesContent() throws IOException, InterruptedException {
        stubResponse(200, "{\"choices\":[{\"message\":{\"content\":\"{\\\"confidence\\\":\\\"medium\\\"}\"}}]}");

        LocalLlmAnalysisClient client = new LocalLlmAnalysisClient(
                objectMapper, httpClient, "http://localhost:11434", "llama3.1", 30);

        Optional<String> result = client.analyze("system prompt", "user prompt");

        assertThat(result).contains("{\"confidence\":\"medium\"}");
    }

    @Test
    void returnsEmptyOnNonSuccessStatus() throws IOException, InterruptedException {
        stubResponse(500, "");

        LocalLlmAnalysisClient client = new LocalLlmAnalysisClient(
                objectMapper, httpClient, "http://localhost:11434", "llama3.1", 30);

        assertThat(client.analyze("system", "user")).isEmpty();
    }

    @Test
    void trimsTrailingSlashFromBaseUrl() throws IOException, InterruptedException {
        stubResponse(200, "{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}");

        LocalLlmAnalysisClient client = new LocalLlmAnalysisClient(
                objectMapper, httpClient, "http://localhost:11434/", "llama3.1", 30);

        assertThat(client.analyze("system", "user")).contains("ok");
    }

    @SuppressWarnings("unchecked")
    private void stubResponse(int statusCode, String body) throws IOException, InterruptedException {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }
}
