package com.miniproject.backend.skills;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniproject.backend.artifact.Evidence;
import com.miniproject.backend.integrations.GitLogReader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Primary
@Component
@ConditionalOnProperty(name = "analysis.hermes-version.provider", havingValue = "llm")
public class LlmHermesVersionAdvisorSynthesizer implements HermesVersionAdvisorSynthesizer {

    /** feat: commits can run into the hundreds -- keep the prompt bounded. */
    private static final int MAX_FEATURE_COMMITS_IN_PROMPT = 80;

    private static final String SYSTEM_PROMPT = """
            You are helping an analyst decide whether to update a self-hosted fork of an actively-developed
            open source project (Hermes, a multi-platform AI agent gateway) to a newer upstream commit/tag.
            The team uses this Hermes deployment as an incident-response agent, bridged to a separate
            "mini-Project" analyst workbench that tracks Hermes's handoff status and reasons about which
            upstream changes are safe to adopt.

            You have two jobs:
            1. Recommend a specific ref to adopt, explain why, and flag concrete risks — do not recommend
               adopting past a commit that touches a patched file without calling it out explicitly.
            2. Separately, you are given a list of upstream commits whose message starts with "feat:" (new
               features). Identify which ones look like they would be a genuinely useful enhancement for this
               team's incident-response / analyst-workflow use case (not just "novel"), and summarize each in
               one plain sentence. Skip anything irrelevant to that use case (e.g. desktop-app-only UI work) —
               it is fine and expected for suggested_enhancements to be empty or short.

            Return strict JSON only. Do not include markdown.
            """;

    private final AiAnalysisClient aiAnalysisClient;
    private final RuleBasedHermesVersionAdvisorSynthesizer fallback;
    private final ObjectMapper objectMapper;

    public LlmHermesVersionAdvisorSynthesizer(
            AiAnalysisClient aiAnalysisClient,
            RuleBasedHermesVersionAdvisorSynthesizer fallback,
            ObjectMapper objectMapper) {
        this.aiAnalysisClient = aiAnalysisClient;
        this.fallback = fallback;
        this.objectMapper = objectMapper;
    }

    @Override
    public HermesVersionAdvisorResult synthesize(String repoPath, GitLogReader.GitFacts facts) {
        String prompt = buildPrompt(repoPath, facts);
        return aiAnalysisClient.analyze(SYSTEM_PROMPT, prompt)
                .flatMap(raw -> parseResult(raw, repoPath, facts))
                .orElseGet(() -> fallback.synthesize(repoPath, facts));
    }

    private String buildPrompt(String repoPath, GitLogReader.GitFacts facts) {
        StringBuilder touched = new StringBuilder();
        for (GitLogReader.TouchedCommit commit : facts.touchedWatchedFiles()) {
            touched.append("- ").append(commit.commit()).append(": ").append(commit.subject()).append('\n');
        }
        StringBuilder features = new StringBuilder();
        List<GitLogReader.TouchedCommit> featureCommits = facts.featureCommits();
        int shown = Math.min(featureCommits.size(), MAX_FEATURE_COMMITS_IN_PROMPT);
        for (int i = 0; i < shown; i++) {
            features.append("- ").append(featureCommits.get(i).subject()).append('\n');
        }
        if (featureCommits.size() > shown) {
            features.append("... and ").append(featureCommits.size() - shown).append(" more feat: commits not shown\n");
        }

        return """
                Repo: %s
                Commits behind upstream: %d
                Commits ahead of upstream (local-only): %d
                Latest upstream tag reachable: %s
                Commits touching watched/patched files:
                %s

                Upstream feat: commits since local diverged (%d total):
                %s

                Return this JSON shape exactly:
                {
                  "recommended_ref": "a specific tag/commit to adopt, or 'manual review required before adopting', or 'already up to date'",
                  "rationale": "why this is the right call",
                  "risks_if_adopted": ["concrete risk or thing to verify before adopting"],
                  "suggested_enhancements": ["one sentence per feat: commit that looks genuinely useful for this team's incident-response/analyst workflow -- can be empty"]
                }
                """.formatted(
                repoPath,
                facts.commitsBehind(),
                facts.commitsAhead(),
                facts.latestTag() == null ? "(none)" : facts.latestTag(),
                touched.isEmpty() ? "(none)" : touched.toString(),
                featureCommits.size(),
                features.isEmpty() ? "(none)" : features.toString());
    }

    private Optional<HermesVersionAdvisorResult> parseResult(
            String rawJson, String repoPath, GitLogReader.GitFacts facts) {
        try {
            JsonNode root = objectMapper.readTree(stripCodeFence(rawJson));
            String recommendedRef = root.path("recommended_ref").asText("").trim();
            String rationale = root.path("rationale").asText("").trim();
            List<String> risks = stringList(root.path("risks_if_adopted"));
            List<String> suggestedEnhancements = stringList(root.path("suggested_enhancements"));
            if (recommendedRef.isBlank() || rationale.isBlank()) {
                return Optional.empty();
            }
            List<Evidence> evidence = List.of(new Evidence(
                    "%d commits behind, %d touching watched files, %d/%d feat: commits judged useful".formatted(
                            facts.commitsBehind(), facts.touchedWatchedFiles().size(),
                            suggestedEnhancements.size(), facts.featureCommits().size()),
                    "hermes-version-advisor (LLM)"));
            return Optional.of(new HermesVersionAdvisorResult(
                    repoPath,
                    facts.commitsBehind(),
                    facts.commitsAhead(),
                    facts.latestTag(),
                    facts.watchedPaths(),
                    facts.touchedWatchedFiles(),
                    facts.featureCommits(),
                    recommendedRef,
                    rationale,
                    risks,
                    suggestedEnhancements,
                    evidence));
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

    private List<String> stringList(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (!node.isArray()) {
            return values;
        }
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                values.add(item.asText().trim());
            }
        }
        return values;
    }
}
