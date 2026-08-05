package com.miniproject.backend.integrations;

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
 * Scrapes github.com/trending — GitHub has no official Trending API, only
 * the web page. Regex-parsed against real page structure (verified live
 * against github.com/trending), not guessed; expected to need occasional
 * maintenance if GitHub changes the page markup. Read-only, GET-only, same
 * unauthenticated-HttpClient trust model as GitHubPrReader.
 */
@Component
public class GitHubTrendingFetcher {

    private static final Pattern ARTICLE = Pattern.compile("<article class=\"Box-row\">(.*?)</article>", Pattern.DOTALL);
    private static final Pattern REPO_NAME = Pattern.compile(
            "<h2[^>]*class=\"h3 lh-condensed\">.*?href=\"/([^/\"]+)/([^/\"]+)\"", Pattern.DOTALL);
    private static final Pattern DESCRIPTION = Pattern.compile(
            "<p class=\"col-9 color-fg-muted my-1[^\"]*\">\\s*(.*?)\\s*</p>", Pattern.DOTALL);
    private static final Pattern STARS = Pattern.compile(
            "/stargazers\"[^>]*>.*?</svg>\\s*([0-9,]+)\\s*</a>", Pattern.DOTALL);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public record TrendingRepo(String fullName, String description, String stars) {
    }

    public List<TrendingRepo> fetchTop(int limit) {
        return parseHtml(fetchHtml(), limit);
    }

    /** Split out from fetchTop() so the regex parsing is testable against a fixture, without a network call. */
    private List<TrendingRepo> parseHtml(String html, int limit) {
        List<TrendingRepo> repos = new ArrayList<>();
        Matcher articles = ARTICLE.matcher(html);
        while (articles.find() && repos.size() < limit) {
            String block = articles.group(1);
            Matcher nameMatcher = REPO_NAME.matcher(block);
            if (!nameMatcher.find()) {
                continue;
            }
            String fullName = nameMatcher.group(1) + "/" + nameMatcher.group(2);
            Matcher descMatcher = DESCRIPTION.matcher(block);
            String description = descMatcher.find() ? descMatcher.group(1).replaceAll("\\s+", " ").trim() : "";
            Matcher starsMatcher = STARS.matcher(block);
            String stars = starsMatcher.find() ? starsMatcher.group(1).trim() : "";
            repos.add(new TrendingRepo(fullName, description, stars));
        }
        return repos;
    }

    private String fetchHtml() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://github.com/trending"))
                    .header("User-Agent", "mini-project-backend")
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new ExternalConnectorException("github.com/trending returned HTTP " + response.statusCode());
            }
            return response.body();
        } catch (IOException e) {
            throw new ExternalConnectorException("Failed to reach github.com/trending", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExternalConnectorException("Interrupted while calling github.com/trending", e);
        }
    }
}
