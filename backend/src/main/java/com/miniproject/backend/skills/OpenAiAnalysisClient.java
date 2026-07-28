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

@Component
@Primary
@ConditionalOnProperty(name = "analysis.llm.provider", havingValue = "openai")
public class OpenAiAnalysisClient implements AiAnalysisClient {

    private static final URI RESPONSES_API = URI.create("https://api.openai.com/v1/responses");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String apiKey;
    private final String model;

    public OpenAiAnalysisClient(
            ObjectMapper objectMapper,
            @Value("${openai.api-key:}") String apiKey,
            @Value("${openai.model:gpt-5-mini}") String model) {
        this(objectMapper, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), apiKey, model);
    }

    OpenAiAnalysisClient(ObjectMapper objectMapper, HttpClient httpClient, String apiKey, String model) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null || model.isBlank() ? "gpt-5-mini" : model.trim();
    }

    @Override
    public Optional<String> analyze(String systemPrompt, String userPrompt) {
        if (apiKey.isBlank()) {
            return Optional.empty();
        }

        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "input", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt))));

            HttpRequest request = HttpRequest.newBuilder(RESPONSES_API)
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode outputText = root.path("output_text");
            if (outputText.isTextual() && !outputText.asText().isBlank()) {
                return Optional.of(outputText.asText());
            }

            return extractTextFromOutput(root);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        }
    }

    private Optional<String> extractTextFromOutput(JsonNode root) {
        JsonNode output = root.path("output");
        if (!output.isArray()) {
            return Optional.empty();
        }

        StringBuilder text = new StringBuilder();
        for (JsonNode item : output) {
            JsonNode content = item.path("content");
            if (!content.isArray()) {
                continue;
            }
            for (JsonNode part : content) {
                JsonNode partText = part.path("text");
                if (partText.isTextual()) {
                    text.append(partText.asText()).append('\n');
                }
            }
        }

        String value = text.toString().trim();
        return value.isBlank() ? Optional.empty() : Optional.of(value);
    }
}
