package com.miniproject.backend.skills;

import com.miniproject.backend.mcp.ProjectGraphClient;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * skills/code-qa.md, Week 1 shape: deterministic tool calls (this class
 * decides what to fetch), synthesis only is delegated to
 * {@link AnswerSynthesizer}. See docs/architecture.md - the coordinator/skill
 * layer stays deterministic until the Week 3 tool-use-loop upgrade
 * described in agents/project-analyst-agent.md.
 */
@Component
public class CodeQaSkill {

    private static final Pattern IDENTIFIER = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]{2,}");
    private static final Set<String> STOPWORDS = Set.of(
            "what", "does", "the", "depend", "dependent", "dependency", "dependencies",
            "change", "changes", "changing", "breaks", "break", "affect", "affects", "affected",
            "and", "for", "with", "this", "that", "from", "into", "did", "who", "calls", "call");

    private final ProjectGraphClient graphClient;
    private final AnswerSynthesizer synthesizer;

    public CodeQaSkill(ProjectGraphClient graphClient, AnswerSynthesizer synthesizer) {
        this.graphClient = graphClient;
        this.synthesizer = synthesizer;
    }

    public CodeQaResult run(String question) {
        List<Map<String, Object>> resolvedEndpoints = extractCandidates(question).stream()
                .map(graphClient::getEndpointInfo)
                .filter(info -> Boolean.TRUE.equals(info.get("found")))
                .toList();

        Map<String, Object> issueSearch = graphClient.searchIssues(question);

        return synthesizer.synthesize(question, resolvedEndpoints, issueSearch);
    }

    private Set<String> extractCandidates(String question) {
        Matcher matcher = IDENTIFIER.matcher(question);
        Set<String> candidates = new LinkedHashSet<>();
        while (matcher.find()) {
            String token = matcher.group();
            if (!STOPWORDS.contains(token.toLowerCase(Locale.ROOT))) {
                candidates.add(token);
            }
        }
        return candidates;
    }
}
