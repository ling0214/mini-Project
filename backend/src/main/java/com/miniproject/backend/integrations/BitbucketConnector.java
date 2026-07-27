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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class BitbucketConnector {

    private static final Pattern BITBUCKET_PR = Pattern.compile(
            "bitbucket\\.org/([^/]+)/([^/]+)/(?:pull-requests|pullrequests)/(\\d+)");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper json;
    private final boolean enabled;
    private final String apiBaseUrl;
    private final String username;
    private final String appPassword;

    public BitbucketConnector(
            ObjectMapper json,
            @Value("${integrations.bitbucket.enabled:false}") boolean enabled,
            @Value("${integrations.bitbucket.api-base-url:https://api.bitbucket.org/2.0}") String apiBaseUrl,
            @Value("${integrations.bitbucket.username:}") String username,
            @Value("${integrations.bitbucket.app-password:}") String appPassword) {
        this.json = json;
        this.enabled = enabled;
        this.apiBaseUrl = trimTrailingSlash(apiBaseUrl);
        this.username = username;
        this.appPassword = appPassword;
    }

    public ConnectorResult commentOnPr(String prUrl, String comment, boolean dryRun) {
        PullRequestRef ref = parse(prUrl);
        if (dryRun || !isConfigured()) {
            return new ConnectorResult(
                    "DRY_RUN",
                    ref == null ? null : ref.workspace() + "/" + ref.repo() + "#" + ref.number(),
                    prUrl,
                    isConfigured()
                            ? "Bitbucket dry-run: reviewed artifact is ready to comment on the PR."
                            : "Bitbucket connector is not configured. Set integrations.bitbucket.* values to enable real comment.",
                    true);
        }
        if (ref == null) {
            throw new IllegalArgumentException("Bitbucket PR URL is required, expected bitbucket.org/workspace/repo/pull-requests/123");
        }

        try {
            Map<String, Object> body = Map.of("content", Map.of("raw", comment));
            String url = apiBaseUrl + "/repositories/" + ref.workspace() + "/" + ref.repo()
                    + "/pullrequests/" + ref.number() + "/comments";
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Basic " + basic(username, appPassword))
                    .header("User-Agent", "mini-project-backend")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 && response.statusCode() != 201) {
                throw new ExternalConnectorException(
                        "Bitbucket API returned HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonNode parsed = json.readTree(response.body());
            String id = parsed.path("id").asText("");
            String html = parsed.path("links").path("html").path("href").asText(prUrl);
            return new ConnectorResult("COMMENTED", id, html, "Bitbucket PR comment created from reviewed artifact.", false);
        } catch (IOException e) {
            throw new ExternalConnectorException("Failed to comment on Bitbucket PR", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalConnectorException("Interrupted while commenting on Bitbucket PR", e);
        }
    }

    private PullRequestRef parse(String prUrl) {
        if (prUrl == null || prUrl.isBlank()) {
            return null;
        }
        Matcher matcher = BITBUCKET_PR.matcher(prUrl);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Not a recognisable Bitbucket PR URL: " + prUrl);
        }
        return new PullRequestRef(matcher.group(1), matcher.group(2), Integer.parseInt(matcher.group(3)));
    }

    private boolean isConfigured() {
        return enabled && !apiBaseUrl.isBlank() && !username.isBlank() && !appPassword.isBlank();
    }

    private static String basic(String username, String password) {
        return Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private static String trimTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private record PullRequestRef(String workspace, String repo, int number) {
    }
}
