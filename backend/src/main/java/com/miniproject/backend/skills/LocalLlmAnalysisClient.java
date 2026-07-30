package com.miniproject.backend.skills;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Talks to a self-hosted model instead of a cloud provider — for companies
 * whose AI policy won't allow requirement text to leave the network (Section:
 * supervisor review item 4). Targets the OpenAI-compatible /v1/chat/completions
 * shape rather than OpenAI's own /v1/responses API, since that's the common
 * denominator every local-model server exposes (Ollama, LM Studio, vLLM,
 * llama.cpp server) — same LlmRequirementAnalysisSynthesizer contract as
 * OpenAiAnalysisClient, just a different endpoint/request/response shape and
 * a longer default timeout (local inference on modest hardware is often much
 * slower than a cloud API).
 */
@Component
@Primary
@ConditionalOnProperty(name = "analysis.llm.provider", havingValue = "local")
public class LocalLlmAnalysisClient implements AiAnalysisClient {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI chatCompletionsUrl;
    private final String model;
    private final long timeoutSeconds;

    public LocalLlmAnalysisClient(
            ObjectMapper objectMapper,
            @Value("${local-llm.base-url:http://localhost:11434}") String baseUrl,
            @Value("${local-llm.model:llama3.1}") String model,
            @Value("${local-llm.timeout-seconds:120}") long timeoutSeconds) {
        this(objectMapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                baseUrl, model, timeoutSeconds);
    }

    LocalLlmAnalysisClient(
            ObjectMapper objectMapper, HttpClient httpClient, String baseUrl, String model, long timeoutSeconds) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        String normalizedBase = (baseUrl == null || baseUrl.isBlank())
                ? "http://localhost:11434" : baseUrl.trim().replaceAll("/+$", "");
        this.chatCompletionsUrl = URI.create(normalizedBase + "/v1/chat/completions");
        this.model = (model == null || model.isBlank()) ? "llama3.1" : model.trim();
        this.timeoutSeconds = timeoutSeconds <= 0 ? 120 : timeoutSeconds;
    }

    @Override
    public Optional<String> analyze(String systemPrompt, String userPrompt) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "stream", false,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt))));

            HttpRequest request = HttpRequest.newBuilder(chatCompletionsUrl)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (content.isTextual() && !content.asText().isBlank()) {
                return Optional.of(content.asText());
            }
            return Optional.empty();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }
}
