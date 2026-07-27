package com.miniproject.backend.github;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only GitHub PR reader: fetches a public PR's title and changed-file
 * patches over the unauthenticated GitHub REST API (60 req/hour/IP, fine
 * for a demo) so a PR can be fed into {@code impact-analysis} as an
 * alternative to typing a change request by hand.
 *
 * Deliberately GET-only — no comment, label, or status-check write-back.
 * See docs/proposal.md Chapter 15: write access to GitHub/Jira is future
 * work, not this slice. This class is the smallest verifiable step toward
 * that — proving the connector pattern works before any write path exists.
 */
@Component
public class GitHubPrReader {

    private static final Pattern PR_URL = Pattern.compile("github\\.com/([^/]+)/([^/]+)/pull/(\\d+)");
    private static final int MAX_PATCH_CHARS_PER_FILE = 4000;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper json = new ObjectMapper();

    public PrSummary fetch(String prUrl) {
        Matcher matcher = PR_URL.matcher(prUrl);
        if (!matcher.find()) {
            throw new IllegalArgumentException(
                    "Not a recognisable GitHub PR URL (expected .../owner/repo/pull/123): " + prUrl);
        }
        String owner = matcher.group(1);
        String repo = matcher.group(2);
        int number = Integer.parseInt(matcher.group(3));

        JsonNode pr = get("https://api.github.com/repos/" + owner + "/" + repo + "/pulls/" + number);
        JsonNode files = get("https://api.github.com/repos/" + owner + "/" + repo + "/pulls/" + number + "/files?per_page=100");

        String title = pr.path("title").asText("");
        List<PrFile> changedFiles = new ArrayList<>();
        if (files.isArray()) {
            for (JsonNode f : files) {
                String patch = f.path("patch").asText("");
                if (patch.length() > MAX_PATCH_CHARS_PER_FILE) {
                    patch = patch.substring(0, MAX_PATCH_CHARS_PER_FILE);
                }
                changedFiles.add(new PrFile(f.path("filename").asText(""), f.path("status").asText(""), patch));
            }
        }
        return new PrSummary(owner, repo, number, title, changedFiles);
    }

    /** Flattens a PrSummary into free text so it can reuse ImpactAnalysisSkill's existing extraction unchanged. */
    public String toChangeRequestText(PrSummary pr) {
        StringBuilder sb = new StringBuilder();
        sb.append("PR #").append(pr.number()).append(" in ").append(pr.owner()).append('/').append(pr.repo())
                .append(": ").append(pr.title()).append(". Changed files: ");
        for (PrFile f : pr.files()) {
            sb.append(f.filename()).append(" (").append(f.status()).append("); ");
        }
        sb.append(" Diff excerpts: ");
        for (PrFile f : pr.files()) {
            sb.append(f.patch()).append(' ');
        }
        return sb.toString();
    }

    private JsonNode get(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "mini-project-backend")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new GitHubPrException("GitHub API returned HTTP " + response.statusCode() + " for " + url);
            }
            return json.readTree(response.body());
        } catch (IOException e) {
            throw new GitHubPrException("Failed to reach GitHub API at " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubPrException("Interrupted while calling GitHub API at " + url, e);
        }
    }

    public record PrFile(String filename, String status, String patch) {
    }

    public record PrSummary(String owner, String repo, int number, String title, List<PrFile> files) {
    }
}
