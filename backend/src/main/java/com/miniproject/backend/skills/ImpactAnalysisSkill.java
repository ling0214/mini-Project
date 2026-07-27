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
 * skills/impact-analysis.md: extract candidate entry points from a free-text
 * change request, call trace_impact per candidate for the blast radius, and
 * search_issues once over the whole request to ground risk notes in real
 * precedent. Deterministic tool calls only, same "skill decides what to
 * fetch, synthesizer decides how to phrase it" split as {@link CodeQaSkill}.
 */
@Component
public class ImpactAnalysisSkill {

    private static final Pattern IDENTIFIER = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]{2,}");
    private static final Set<String> STOPWORDS = Set.of(
            "what", "does", "the", "add", "adding", "remove", "removing", "change", "changes",
            "changing", "update", "updating", "instead", "alternative", "screen", "page", "flow",
            "feature", "and", "for", "with", "this", "that", "from", "into", "should", "would",
            "when", "where", "how", "will", "impact", "affect", "affects", "affected",
            "automatic", "automatically", "payment", "payments", "gateway", "timeout", "timeouts",
            "times", "out", "retry", "retries", "retrying", "business", "wants", "want", "always",
            "use", "uses", "using", "latest", "cached", "before", "after", "apply", "applying",
            "support", "supports", "supporting", "confirmation", "login", "biometric", "pin",
            "discount", "code", "codes", "on", "an", "a", "to", "of", "in", "not", "no", "all",
            "any", "outright", "profile", "email", "stale", "wrong", "address",
            "def", "class", "self", "import", "return", "true", "false", "none", "null",
            "changed", "files", "diff", "excerpts", "status", "modified", "added", "removed",
            "renamed", "pull", "com", "github", "https", "http", "www");
    private static final int MAX_HOPS = 2;

    private final ProjectGraphClient graphClient;
    private final ImpactAnalysisSynthesizer synthesizer;

    public ImpactAnalysisSkill(ProjectGraphClient graphClient, ImpactAnalysisSynthesizer synthesizer) {
        this.graphClient = graphClient;
        this.synthesizer = synthesizer;
    }

    public ImpactAnalysisResult run(String changeRequest) {
        Set<String> candidates = extractCandidates(changeRequest);
        List<Map<String, Object>> traces = candidates.stream()
                .map(name -> graphClient.traceImpact(name, MAX_HOPS))
                .toList();

        Map<String, Object> issueSearch = graphClient.searchIssues(changeRequest);

        return synthesizer.synthesize(changeRequest, candidates, traces, issueSearch);
    }

    private Set<String> extractCandidates(String changeRequest) {
        Matcher matcher = IDENTIFIER.matcher(changeRequest);
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
