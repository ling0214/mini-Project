package com.miniproject.backend.integrations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

@Component
public class JiraConnector {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper json;
    private final boolean enabled;
    private final String baseUrl;
    private final String email;
    private final String apiToken;
    private final String projectKey;
    private final String issueType;
    private final String authMode;
    private final String cloudId;

    public JiraConnector(
            ObjectMapper json,
            @Value("${integrations.jira.enabled:false}") boolean enabled,
            @Value("${integrations.jira.base-url:}") String baseUrl,
            @Value("${integrations.jira.email:}") String email,
            @Value("${integrations.jira.api-token:}") String apiToken,
            @Value("${integrations.jira.project-key:}") String projectKey,
            @Value("${integrations.jira.issue-type:Task}") String issueType,
            @Value("${integrations.jira.auth-mode:basic}") String authMode,
            @Value("${integrations.jira.cloud-id:}") String cloudId) {
        this.json = json;
        this.enabled = enabled;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.email = email;
        this.apiToken = apiToken;
        this.projectKey = projectKey;
        this.issueType = issueType;
        this.authMode = authMode == null ? "basic" : authMode.trim().toLowerCase();
        this.cloudId = cloudId == null ? "" : cloudId.trim();
    }

    public ConnectorResult createIssue(String summary, String description, boolean dryRun) {
        if (dryRun || !isConfigured()) {
            return new ConnectorResult(
                    "DRY_RUN",
                    null,
                    null,
                    isConfigured()
                            ? "Jira dry-run: reviewed artifact is ready to create a Jira issue."
                            : "Jira connector is not configured. Set integrations.jira.* values to enable real create.",
                    true);
        }

        try {
            Map<String, Object> body = Map.of("fields", Map.of(
                    "project", Map.of("key", projectKey),
                    "summary", summary,
                    "description", adfDescription(description),
                    "issuetype", Map.of("name", issueType)));
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiBaseUrl() + "/rest/api/3/issue"))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Basic " + basic(email, apiToken))
                    .header("User-Agent", "mini-project-backend")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 && response.statusCode() != 201) {
                throw new ExternalConnectorException(
                        "Jira API returned HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonNode parsed = json.readTree(response.body());
            String key = parsed.path("key").asText("");
            String url = key.isBlank() ? parsed.path("self").asText(null) : baseUrl + "/browse/" + key;
            return new ConnectorResult("CREATED", key, url, "Jira issue created from reviewed artifact.", false);
        } catch (IOException e) {
            throw new ExternalConnectorException("Failed to create Jira issue", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalConnectorException("Interrupted while creating Jira issue", e);
        }
    }

    private boolean isConfigured() {
        return enabled
                && !baseUrl.isBlank()
                && !email.isBlank()
                && !apiToken.isBlank()
                && !projectKey.isBlank()
                && (!usesScopedToken() || !cloudId.isBlank());
    }

    private String apiBaseUrl() {
        if (usesScopedToken()) {
            return "https://api.atlassian.com/ex/jira/" + cloudId;
        }
        return baseUrl;
    }

    private boolean usesScopedToken() {
        return "scoped".equals(authMode);
    }

    private static Map<String, Object> adfDescription(String text) {
        return Map.of(
                "type", "doc",
                "version", 1,
                "content", java.util.List.of(Map.of(
                        "type", "paragraph",
                        "content", java.util.List.of(Map.of("type", "text", "text", text)))));
    }

    private static String basic(String username, String password) {
        return Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private static String trimTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }
}
