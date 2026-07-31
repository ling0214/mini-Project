package com.miniproject.backend.skills;

import com.miniproject.backend.artifact.Evidence;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Week 1 default: assembles the answer purely from graph/issue facts with
 * no LLM call, so the whole code-qa path is runnable and testable without
 * an Anthropic API key. A ClaudeAnswerSynthesizer implementing the same
 * interface is the planned Week 2+ swap-in (see docs/architecture.md) —
 * not built yet, since it can't be verified without a live key in this
 * environment.
 */
@Component
public class RuleBasedAnswerSynthesizer implements AnswerSynthesizer {

    @Override
    @SuppressWarnings("unchecked")
    public CodeQaResult synthesize(String question, List<Map<String, Object>> resolvedEndpoints, Map<String, Object> issueSearch) {
        List<Evidence> evidence = new ArrayList<>();
        List<String> ungrounded = new ArrayList<>();
        StringBuilder answer = new StringBuilder();

        if (resolvedEndpoints.isEmpty()) {
            ungrounded.add("No identifier in the question resolved against the project graph.");
        }

        for (Map<String, Object> info : resolvedEndpoints) {
            String name = String.valueOf(info.get("name"));
            String file = String.valueOf(info.get("file"));
            Object line = info.get("line");
            List<Object> calls = (List<Object>) info.getOrDefault("calls", List.of());
            List<Object> calledBy = (List<Object>) info.getOrDefault("called_by", List.of());
            String source = file + ":" + line;

            answer.append(name).append(" (").append(source).append(") calls ")
                    .append(calls.isEmpty() ? "nothing in the graph" : calls)
                    .append("; called by ")
                    .append(calledBy.isEmpty() ? "nothing in the graph (likely an entry point)" : calledBy)
                    .append(". ");
            evidence.add(new Evidence(name + " dependencies", source));
        }

        List<Map<String, Object>> matches = (List<Map<String, Object>>) issueSearch.getOrDefault("matches", List.of());
        if (!matches.isEmpty()) {
            answer.append("Related issues: ");
            for (Map<String, Object> issue : matches) {
                answer.append("#").append(issue.get("id")).append(" (").append(issue.get("state")).append(") ")
                        .append(issue.get("title")).append("; ");
                evidence.add(new Evidence(String.valueOf(issue.get("title")), "issue #" + issue.get("id")));
            }
        }

        Map<String, Object> projectContext = (Map<String, Object>) issueSearch.getOrDefault("project_context", Map.of());
        List<Map<String, Object>> contextMatches = (List<Map<String, Object>>) projectContext.getOrDefault("matches", List.of());
        if (!contextMatches.isEmpty()) {
            answer.append("Relevant project context: ");
            for (Map<String, Object> match : contextMatches.stream().limit(5).toList()) {
                String name = String.valueOf(match.get("name"));
                String file = String.valueOf(match.get("file"));
                Object line = match.getOrDefault("line", 1);
                String reason = String.valueOf(match.getOrDefault("reason", "matched the question"));
                answer.append(name).append(" (").append(file).append(":").append(line).append(") - ")
                        .append(reason).append(". ");
                evidence.add(new Evidence(name + " project context", file + ":" + line));
            }
        }

        if (answer.isEmpty()) {
            answer.append("Nothing in the project graph or issue tracker matched this question.");
        }

        return new CodeQaResult(answer.toString().trim(), evidence, ungrounded);
    }
}
