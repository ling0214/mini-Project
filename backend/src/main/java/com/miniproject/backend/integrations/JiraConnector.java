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
import java.util.ArrayList;
import java.util.List;
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
    private final String acceptanceCriteriaField;

    public JiraConnector(
            ObjectMapper json,
            @Value("${integrations.jira.enabled:false}") boolean enabled,
            @Value("${integrations.jira.base-url:}") String baseUrl,
            @Value("${integrations.jira.email:}") String email,
            @Value("${integrations.jira.api-token:}") String apiToken,
            @Value("${integrations.jira.project-key:}") String projectKey,
            @Value("${integrations.jira.issue-type:Task}") String issueType,
            @Value("${integrations.jira.auth-mode:basic}") String authMode,
            @Value("${integrations.jira.cloud-id:}") String cloudId,
            @Value("${integrations.jira.acceptance-criteria-field:}") String acceptanceCriteriaField) {
        this.json = json;
        this.enabled = enabled;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.email = email;
        this.apiToken = apiToken;
        this.projectKey = projectKey;
        this.issueType = issueType;
        this.authMode = authMode == null ? "basic" : authMode.trim().toLowerCase();
        this.cloudId = cloudId == null ? "" : cloudId.trim();
        this.acceptanceCriteriaField = acceptanceCriteriaField == null ? "" : acceptanceCriteriaField.trim();
    }

    /** Diagnostic: identifies which Atlassian account the configured token actually authenticates as. */
    public JiraIdentity whoAmI() {
        if (!canReadIssues()) {
            throw new ExternalConnectorException("Jira read-only import is not configured.");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiBaseUrl() + "/rest/api/3/myself"))
                    .header("Accept", "application/json")
                    .header("Authorization", authorizationHeader())
                    .header("User-Agent", "mini-project-backend")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ExternalConnectorException(
                        "Jira API returned HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonNode parsed = json.readTree(response.body());
            return new JiraIdentity(
                    parsed.path("displayName").asText(""),
                    parsed.path("emailAddress").asText(""),
                    parsed.path("accountId").asText(""),
                    parsed.path("accountType").asText(""),
                    email,
                    apiBaseUrl());
        } catch (IOException e) {
            throw new ExternalConnectorException("Failed to read Jira identity", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalConnectorException("Interrupted while reading Jira identity", e);
        }
    }

    public record JiraIdentity(
            String displayName, String emailAddress, String accountId, String accountType,
            String configuredEmail, String apiBaseUrl) {
    }

    public JiraIssue fetchIssue(String issueKey) {
        if (!canReadIssues()) {
            throw new ExternalConnectorException("Jira read-only import is not configured.");
        }
        String key = issueKey == null ? "" : issueKey.trim().toUpperCase();
        if (key.isBlank()) {
            throw new IllegalArgumentException("Jira issue key is required");
        }

        try {
            String fields = "summary,description,priority,reporter,comment,created,updated";
            if (!acceptanceCriteriaField.isBlank()) {
                fields += "," + acceptanceCriteriaField;
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiBaseUrl() + "/rest/api/3/issue/" + key + "?fields=" + fields))
                    .header("Accept", "application/json")
                    .header("Authorization", authorizationHeader())
                    .header("User-Agent", "mini-project-backend")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ExternalConnectorException(
                        "Jira API returned HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonNode parsed = json.readTree(response.body());
            JsonNode fieldsNode = parsed.path("fields");
            String resolvedKey = parsed.path("key").asText(key);
            String summary = fieldsNode.path("summary").asText("");
            String description = plainText(fieldsNode.path("description"));
            String priority = fieldsNode.path("priority").path("name").asText("Medium");
            String reporter = firstNonBlank(
                    fieldsNode.path("reporter").path("displayName").asText(""),
                    fieldsNode.path("reporter").path("emailAddress").asText(""),
                    "Jira");
            String acceptanceCriteria = acceptanceCriteriaField.isBlank()
                    ? ""
                    : plainText(fieldsNode.path(acceptanceCriteriaField));
            String comments = commentsText(fieldsNode.path("comment").path("comments"));
            String sourceUrl = baseUrl.isBlank() ? parsed.path("self").asText("") : baseUrl + "/browse/" + resolvedKey;
            String receivedAt = firstNonBlank(fieldsNode.path("updated").asText(""), fieldsNode.path("created").asText(""));
            return new JiraIssue(
                    resolvedKey,
                    summary,
                    priority,
                    reporter,
                    description,
                    acceptanceCriteria,
                    comments,
                    sourceUrl,
                    receivedAt);
        } catch (IOException e) {
            throw new ExternalConnectorException("Failed to read Jira issue", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalConnectorException("Interrupted while reading Jira issue", e);
        }
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
                    .header("Authorization", authorizationHeader())
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

    public boolean canReadIssues() {
        return enabled
                && !baseUrl.isBlank()
                && !apiToken.isBlank()
                && (!usesScopedToken() || !cloudId.isBlank())
                && (usesScopedToken() || !email.isBlank());
    }

    private boolean isConfigured() {
        return canReadIssues()
                && !projectKey.isBlank()
                && !issueType.isBlank();
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

    private String authorizationHeader() {
        if (usesScopedToken()) {
            return "Bearer " + apiToken;
        }
        return "Basic " + basic(email, apiToken);
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

    private static String commentsText(JsonNode commentsNode) {
        if (!commentsNode.isArray()) {
            return "";
        }
        List<String> comments = new ArrayList<>();
        for (JsonNode comment : commentsNode) {
            String author = firstNonBlank(comment.path("author").path("displayName").asText(""), "Jira commenter");
            String body = plainText(comment.path("body"));
            if (!body.isBlank()) {
                comments.add(author + ": " + body);
            }
        }
        return String.join("\n\n", comments);
    }

    private static String plainText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText().trim();
        }
        List<String> parts = new ArrayList<>();
        collectText(node, parts);
        return String.join(" ", parts).replaceAll("\\s+", " ").trim();
    }

    private static void collectText(JsonNode node, List<String> parts) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            String text = node.asText().trim();
            if (!text.isBlank()) {
                parts.add(text);
            }
            return;
        }
        JsonNode textNode = node.get("text");
        if (textNode != null && textNode.isTextual()) {
            String text = textNode.asText().trim();
            if (!text.isBlank()) {
                parts.add(text);
            }
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectText(child, parts);
            }
            return;
        }
        JsonNode content = node.get("content");
        if (content != null) {
            collectText(content, parts);
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    public record JiraIssue(
            String key,
            String title,
            String priority,
            String reporter,
            String description,
            String acceptanceCriteria,
            String comments,
            String sourceUrl,
            String receivedAt) {
    }
}
