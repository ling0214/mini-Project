package com.miniproject.backend.skills;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniproject.backend.artifact.Evidence;
import com.miniproject.backend.integrations.GitHubTrendingFetcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Primary
@Component
@ConditionalOnProperty(name = "analysis.trending.provider", havingValue = "llm")
public class LlmTrendingRelevanceSynthesizer implements TrendingRelevanceSynthesizer {

    private static final String SYSTEM_PROMPT = """
            You are helping a team that operates Hermes, a self-hosted, multi-platform (Discord/Telegram/email)
            AI agent gateway with a plugin system, skills, persistent memory, and an incident-response plugin
            (RCA generation, PR-package fix flow). Given a short list of today's GitHub Trending repos, judge
            whether each one could plausibly be useful to add to or integrate with a Hermes deployment
            (e.g. a memory provider, an agent skill, a code-analysis tool, a security/audit tool for MCP
            servers) versus being unrelated. Return strict JSON only. Do not include markdown.
            """;

    private final AiAnalysisClient aiAnalysisClient;
    private final RuleBasedTrendingRelevanceSynthesizer fallback;
    private final ObjectMapper objectMapper;

    public LlmTrendingRelevanceSynthesizer(
            AiAnalysisClient aiAnalysisClient,
            RuleBasedTrendingRelevanceSynthesizer fallback,
            ObjectMapper objectMapper) {
        this.aiAnalysisClient = aiAnalysisClient;
        this.fallback = fallback;
        this.objectMapper = objectMapper;
    }

    @Override
    public HermesTrendingDigestResult synthesize(List<GitHubTrendingFetcher.TrendingRepo> candidates) {
        if (candidates.isEmpty()) {
            return new HermesTrendingDigestResult(List.of(), List.of());
        }
        String prompt = buildPrompt(candidates);
        return aiAnalysisClient.analyze(SYSTEM_PROMPT, prompt)
                .flatMap(raw -> parseResult(raw, candidates))
                .orElseGet(() -> fallback.synthesize(candidates));
    }

    private String buildPrompt(List<GitHubTrendingFetcher.TrendingRepo> candidates) {
        StringBuilder sb = new StringBuilder();
        for (GitHubTrendingFetcher.TrendingRepo repo : candidates) {
            sb.append("- ").append(repo.fullName()).append(" (").append(repo.stars()).append(" stars): ")
                    .append(repo.description()).append('\n');
        }
        return """
                Today's GitHub Trending candidates:
                %s

                Return this JSON shape exactly:
                {
                  "candidates": [
                    {"repo_name": "owner/repo", "relevant": true|false, "reasoning": "one sentence why or why not"}
                  ]
                }
                """.formatted(sb);
    }

    private Optional<HermesTrendingDigestResult> parseResult(
            String rawJson, List<GitHubTrendingFetcher.TrendingRepo> candidates) {
        try {
            JsonNode root = objectMapper.readTree(stripCodeFence(rawJson));
            JsonNode candidatesNode = root.path("candidates");
            if (!candidatesNode.isArray() || candidatesNode.isEmpty()) {
                return Optional.empty();
            }
            List<HermesTrendingDigestResult.Candidate> results = new ArrayList<>();
            for (JsonNode item : candidatesNode) {
                String repoName = item.path("repo_name").asText("").trim();
                GitHubTrendingFetcher.TrendingRepo matched = candidates.stream()
                        .filter(repo -> repo.fullName().equalsIgnoreCase(repoName))
                        .findFirst()
                        .orElse(null);
                if (matched == null) {
                    continue;
                }
                boolean relevant = item.path("relevant").asBoolean(false);
                String reasoning = item.path("reasoning").asText("").trim();
                results.add(new HermesTrendingDigestResult.Candidate(
                        matched.fullName(), matched.description(), matched.stars(), relevant, reasoning));
            }
            if (results.isEmpty()) {
                return Optional.empty();
            }
            List<Evidence> evidence = List.of(
                    new Evidence(results.size() + " trending candidate(s) judged", "hermes-trending-digest (LLM)"));
            return Optional.of(new HermesTrendingDigestResult(results, evidence));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private String stripCodeFence(String rawJson) {
        String trimmed = rawJson.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstLineEnd = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstLineEnd >= 0 && lastFence > firstLineEnd) {
            return trimmed.substring(firstLineEnd + 1, lastFence).trim();
        }
        return trimmed;
    }
}
