package com.miniproject.backend.integrations;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Component
public class HermesConnector {

    private static final int DISCORD_CONTENT_LIMIT = 1900;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper json;
    private final boolean enabled;
    private final String discordWebhookUrl;
    private final String targetLabel;

    public HermesConnector(
            ObjectMapper json,
            @Value("${integrations.hermes.enabled:false}") boolean enabled,
            @Value("${integrations.hermes.discord-webhook-url:}") String discordWebhookUrl,
            @Value("${integrations.hermes.target-label:Hermes intake}") String targetLabel) {
        this.json = json;
        this.enabled = enabled;
        this.discordWebhookUrl = discordWebhookUrl == null ? "" : discordWebhookUrl.trim();
        this.targetLabel = targetLabel == null || targetLabel.isBlank() ? "Hermes intake" : targetLabel.trim();
    }

    public ConnectorResult sendTask(String summary, String description, boolean dryRun) {
        if (dryRun || !isConfigured()) {
            return new ConnectorResult(
                    "DRY_RUN",
                    targetLabel,
                    null,
                    isConfigured()
                            ? "Hermes dry-run: reviewed analyst package is ready to send."
                            : "Hermes connector is not configured. Set integrations.hermes.* values to enable real handoff.",
                    true);
        }

        try {
            String content = buildDiscordMessage(summary, description);
            HttpRequest request = HttpRequest.newBuilder(URI.create(discordWebhookUrl))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "mini-project-backend")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("content", content))))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ExternalConnectorException(
                        "Hermes Discord webhook returned HTTP " + response.statusCode() + ": " + response.body());
            }
            return new ConnectorResult(
                    "SENT",
                    targetLabel,
                    null,
                    "Reviewed analyst package sent to Hermes intake.",
                    false);
        } catch (IOException e) {
            throw new ExternalConnectorException("Failed to send handoff to Hermes", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalConnectorException("Interrupted while sending handoff to Hermes", e);
        }
    }

    private boolean isConfigured() {
        return enabled && !discordWebhookUrl.isBlank();
    }

    private static String buildDiscordMessage(String summary, String description) {
        String message = """
                **Analyst Workbench Handoff**
                %s

                %s
                """.formatted(
                blankToFallback(summary, "Reviewed analyst package"),
                blankToFallback(description, "No description provided."));
        return message.length() > DISCORD_CONTENT_LIMIT
                ? message.substring(0, DISCORD_CONTENT_LIMIT) + "\n...[truncated]"
                : message;
    }

    private static String blankToFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
