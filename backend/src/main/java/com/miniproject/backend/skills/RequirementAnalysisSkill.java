package com.miniproject.backend.skills;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * First step of the Analysis pivot's minimal slice (see docs/architecture.md
 * "RequirementAnalysisSkill"): runs before ImpactAnalysisSkill, over the raw
 * change-request text a human typed in, not over the code graph. No MCP call
 * here — this skill has nothing to look up yet, only text to parse. Its
 * candidateAreas output (identifier-like tokens) is deliberately the same
 * shape ImpactAnalysisSkill expects as input, so the two skills chain without
 * a translation step once wired into the workflow.
 */
@Component
public class RequirementAnalysisSkill {

    private static final Pattern IDENTIFIER = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]{2,}");
    private static final Set<String> STOPWORDS = Set.of(
            "the", "a", "an", "and", "or", "but", "for", "with", "this", "that", "from", "into",
            "should", "would", "will", "when", "where", "how", "what", "does", "add", "adding",
            "remove", "removing", "change", "changes", "changing", "update", "updating", "instead",
            "allow", "allows", "allowed", "able", "user", "users", "system", "not", "no", "all",
            "any", "after", "before", "apply", "applying", "can", "to", "of", "in", "on", "if",
            "then", "else", "must", "shall", "cannot", "unless", "always", "never", "customer",
            "customers", "please", "need", "needs", "needed", "want", "wants", "wanted",
            "ticket", "key", "title", "priority", "reporter", "description", "acceptance",
            "criteria", "comments", "notes", "given", "select", "selects", "page", "matching",
            "mbc", "fyp", "supervisor", "stakeholder", "dry", "run", "import",
            "high", "medium", "low", "critical", "only", "shows", "show", "use", "first",
            "future", "enhancement", "required", "now", "available", "records", "responding",
            "help", "browsing", "treated", "confirms", "confirm", "confirmed", "whether",
            "are", "is", "was", "were", "be", "been", "shown");

    private final RequirementAnalysisSynthesizer synthesizer;

    public RequirementAnalysisSkill(RequirementAnalysisSynthesizer synthesizer) {
        this.synthesizer = synthesizer;
    }

    public RequirementAnalysisResult run(String description) {
        List<String> sentences = RuleBasedRequirementAnalysisSynthesizer.splitSentences(description);
        List<String> candidateAreas = new ArrayList<>(extractCandidateAreas(description));
        return synthesizer.synthesize(description, sentences, candidateAreas);
    }

    private Set<String> extractCandidateAreas(String description) {
        Matcher matcher = IDENTIFIER.matcher(description);
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
