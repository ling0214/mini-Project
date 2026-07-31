package com.miniproject.backend.skills;

import com.miniproject.backend.artifact.Evidence;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The "Week 2+ swap-in" RuleBasedAnswerSynthesizer's own comment already
 * called for: turns the same graph/issue facts CodeQaSkill already resolved
 * into a reasoned answer instead of a template, via whichever AiAnalysisClient
 * is currently active (OpenAI / local / claude-cli — Section: analysis.llm.provider).
 * Falls back to the rule-based synthesizer if the AI call comes back empty,
 * same resilience pattern as LlmRequirementAnalysisSynthesizer.
 */
@Primary
@Component
@ConditionalOnProperty(name = "analysis.codeqa.provider", havingValue = "llm")
public class LlmAnswerSynthesizer implements AnswerSynthesizer {

    private static final String SYSTEM_PROMPT = """
            You are a Software Analyst assistant answering a question about a codebase.
            Use ONLY the graph facts and issue matches provided below as ground truth —
            do not invent file names, function names, or behavior not listed there.
            If the provided facts don't contain enough information to answer confidently,
            say so plainly instead of guessing. Keep the answer to 2-5 sentences.
            """;

    private final AiAnalysisClient aiAnalysisClient;
    private final RuleBasedAnswerSynthesizer fallback;

    public LlmAnswerSynthesizer(AiAnalysisClient aiAnalysisClient, RuleBasedAnswerSynthesizer fallback) {
        this.aiAnalysisClient = aiAnalysisClient;
        this.fallback = fallback;
    }

    @Override
    public CodeQaResult synthesize(
            String question, List<Map<String, Object>> resolvedEndpoints, Map<String, Object> issueSearch) {
        String userPrompt = buildUserPrompt(question, resolvedEndpoints, issueSearch);
        Optional<String> aiAnswer = aiAnalysisClient.analyze(SYSTEM_PROMPT, userPrompt);
        if (aiAnswer.isEmpty() || aiAnswer.get().isBlank()) {
            return fallback.synthesize(question, resolvedEndpoints, issueSearch);
        }

        List<Evidence> evidence = new ArrayList<>();
        for (Map<String, Object> info : resolvedEndpoints) {
            String name = String.valueOf(info.get("name"));
            String file = String.valueOf(info.get("file"));
            Object line = info.get("line");
            evidence.add(new Evidence(name + " dependencies", file + ":" + line));
        }
        List<Map<String, Object>> contextMatches = projectContextMatches(issueSearch);
        for (Map<String, Object> match : contextMatches) {
            String name = String.valueOf(match.get("name"));
            String file = String.valueOf(match.get("file"));
            Object line = match.getOrDefault("line", 1);
            evidence.add(new Evidence(name + " project context", file + ":" + line));
        }
        List<String> ungrounded = resolvedEndpoints.isEmpty() && contextMatches.isEmpty()
                ? List.of("No identifier in the question resolved against the project graph; answer is AI reasoning, not a direct graph fact.")
                : List.of();

        return new CodeQaResult(aiAnswer.get().trim(), evidence, ungrounded);
    }

    private String buildUserPrompt(
            String question, List<Map<String, Object>> resolvedEndpoints, Map<String, Object> issueSearch) {
        StringBuilder sb = new StringBuilder();
        sb.append("Question: ").append(question).append("\n\nGraph facts:\n");
        if (resolvedEndpoints.isEmpty()) {
            sb.append("(none found)\n");
        } else {
            for (Map<String, Object> info : resolvedEndpoints) {
                sb.append("- ").append(info.get("name")).append(" at ").append(info.get("file")).append(':')
                        .append(info.get("line")).append(" calls ").append(info.getOrDefault("calls", List.of()))
                        .append("; called by ").append(info.getOrDefault("called_by", List.of())).append('\n');
            }
        }

        sb.append("\nRelated issues:\n");
        Object matches = issueSearch.get("matches");
        if (matches instanceof List<?> list && !list.isEmpty()) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> issue) {
                    sb.append("- #").append(issue.get("id")).append(" (").append(issue.get("state")).append(") ")
                            .append(issue.get("title")).append('\n');
                }
            }
        } else {
            sb.append("(none found)\n");
        }

        sb.append("\nRelevant project context:\n");
        List<Map<String, Object>> contextMatches = projectContextMatches(issueSearch);
        if (contextMatches.isEmpty()) {
            sb.append("(none found)\n");
        } else {
            for (Map<String, Object> match : contextMatches) {
                sb.append("- ").append(match.get("name")).append(" at ").append(match.get("file")).append(':')
                        .append(match.getOrDefault("line", 1)).append(" because ")
                        .append(match.getOrDefault("reason", "it matched the question")).append('\n');
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> projectContextMatches(Map<String, Object> issueSearch) {
        Object projectContext = issueSearch.get("project_context");
        if (projectContext instanceof Map<?, ?> context) {
            Object matches = context.get("matches");
            if (matches instanceof List<?> list) {
                return list.stream()
                        .filter(Map.class::isInstance)
                        .map(item -> (Map<String, Object>) item)
                        .toList();
            }
        }
        return List.of();
    }
}
