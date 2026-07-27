package com.miniproject.backend.skills;

import com.miniproject.backend.artifact.Evidence;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * First cut at RequirementAnalysisSkill's synthesis step: keyword/pattern
 * heuristics only, no LLM call — same "rule-based first" position as
 * RuleBasedImpactAnalysisSynthesizer. Known limitation, stated up front
 * rather than discovered by a reviewer: this can flag *lexical* signals of
 * ambiguity (hedge words, missing actor, no stated outcome) but cannot judge
 * whether a requirement is actually clear. A future LLM-backed
 * RequirementAnalysisSynthesizer is expected to replace this for real
 * semantic judgment — see docs/architecture.md.
 */
@Component
public class RuleBasedRequirementAnalysisSynthesizer implements RequirementAnalysisSynthesizer {

    private static final List<String> HEDGE_WORDS = List.of(
            "tbd", "maybe", "possibly", "somehow", "eventually", "at some point",
            "if possible", "e.g.", "etc", "and so on", "later", "not sure", "might",
            "should probably", "i think", "roughly", "approximately");

    private static final List<String> RULE_MARKERS = List.of(
            "must", "should", "shall", "cannot", "can't", "only if", "unless", "always", "never");

    private static final List<String> ACTOR_WORDS = List.of(
            "user", "customer", "analyst", "admin", "administrator", "agent", "tester",
            "system", "reviewer", "manager", "client", "applicant", "policyholder");

    private static final int MIN_WORDS_FOR_CONCRETE_SCOPE = 8;

    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?])\\s+");

    @Override
    public RequirementAnalysisResult synthesize(String description, List<String> sentences, List<String> candidateAreas) {
        String lower = description.toLowerCase(Locale.ROOT);

        List<String> missingInformation = new ArrayList<>();
        List<RequirementAnalysisResult.Ambiguity> ambiguities = new ArrayList<>();
        List<String> businessRules = new ArrayList<>();

        int wordCount = description.trim().isEmpty() ? 0 : description.trim().split("\\s+").length;
        if (wordCount < MIN_WORDS_FOR_CONCRETE_SCOPE) {
            missingInformation.add("Description is only " + wordCount + " word(s) — too short to extract a concrete scope; ask the requester for more detail.");
        }

        boolean hasActor = ACTOR_WORDS.stream().anyMatch(lower::contains);
        if (!hasActor) {
            missingInformation.add("No actor/role (e.g. \"user\", \"customer\", \"analyst\") found in the description — unclear who this change is for.");
        }

        if (candidateAreas.isEmpty()) {
            missingInformation.add("No specific system/module/feature name found in the description — unclear what part of the system is affected.");
        }

        for (String hedge : HEDGE_WORDS) {
            if (lower.contains(hedge)) {
                ambiguities.add(new RequirementAnalysisResult.Ambiguity(
                        "Hedge word \"" + hedge + "\" found — the requirement is not stated as a firm decision.", description));
            }
        }

        for (String sentence : sentences) {
            String sentenceLower = sentence.toLowerCase(Locale.ROOT);
            if (RULE_MARKERS.stream().anyMatch(sentenceLower::contains)) {
                businessRules.add(sentence.trim());
            }
        }

        List<String> assumptions = businessRules.isEmpty()
                ? List.of("No explicit business rule (must/should/shall/unless/...) was stated; assume none beyond what's written.")
                : List.of();

        String confidence = (!ambiguities.isEmpty() || !missingInformation.isEmpty()) ? "low" : "medium";

        List<Evidence> evidence = new ArrayList<>();
        for (String rule : businessRules) {
            evidence.add(new Evidence(rule, "requirement text"));
        }

        return new RequirementAnalysisResult(
                businessRules, ambiguities, missingInformation, assumptions, candidateAreas, confidence, evidence);
    }

    static List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        for (String s : SENTENCE_SPLIT.split(text.trim())) {
            if (!s.isBlank()) {
                sentences.add(s.trim());
            }
        }
        return sentences;
    }
}
