package com.miniproject.backend.skills;

import com.miniproject.backend.artifact.Evidence;
import com.miniproject.backend.memory.SimilarPastChange;

import java.util.List;

/**
 * Output shape for RequirementAnalysisSkill: what a rule-based (or, later,
 * LLM-backed — see RequirementAnalysisSynthesizer) pass over a free-text
 * requirement/change description can say about it before any code-graph
 * lookup happens. Deliberately has no "answer" field the way CodeQaResult
 * does — this skill's job is to surface gaps, not resolve them.
 *
 * similarPastChanges is deliberately NOT populated by either synthesizer —
 * neither knows about past-artifact memory, single responsibility stays
 * "analyze this text". CoordinatorService appends it after synthesis, same
 * pattern as impactAnalysisFromPr() appending PR evidence post-synthesis.
 */
public record RequirementAnalysisResult(
        List<String> businessRules,
        String businessValue,
        ScopeBoundary scopeBoundary,
        List<Ambiguity> ambiguities,
        List<String> missingInformation,
        List<String> assumptions,
        List<AnalystConcern> analystConcerns,
        List<ProjectRisk> projectRisks,
        List<String> potentialAffectedAreas,
        String confidence,
        List<Evidence> evidence,
        List<SimilarPastChange> similarPastChanges) {

    public record ScopeBoundary(
            List<String> inScope,
            List<String> outOfScope,
            List<String> dependencies) {
    }

    public record Ambiguity(String note, String evidence) {
    }

    public record AnalystConcern(String category, String severity, String note, String evidence, String question) {
    }

    public record ProjectRisk(
            String priority,
            String severity,
            String area,
            String reason,
            String mitigation,
            String owner) {
    }
}
