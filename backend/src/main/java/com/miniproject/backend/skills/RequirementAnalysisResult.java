package com.miniproject.backend.skills;

import com.miniproject.backend.artifact.Evidence;

import java.util.List;

/**
 * Output shape for RequirementAnalysisSkill: what a rule-based (or, later,
 * LLM-backed — see RequirementAnalysisSynthesizer) pass over a free-text
 * requirement/change description can say about it before any code-graph
 * lookup happens. Deliberately has no "answer" field the way CodeQaResult
 * does — this skill's job is to surface gaps, not resolve them.
 */
public record RequirementAnalysisResult(
        List<String> businessRules,
        List<Ambiguity> ambiguities,
        List<String> missingInformation,
        List<String> assumptions,
        List<String> potentialAffectedAreas,
        String confidence,
        List<Evidence> evidence) {

    public record Ambiguity(String note, String evidence) {
    }
}

