package com.miniproject.backend.skills;

import com.miniproject.backend.artifact.Evidence;
import com.miniproject.backend.memory.SimilarPastChange;

import java.util.List;

/** Output shape from skills/impact-analysis.md. */
public record ImpactAnalysisResult(
        List<AffectedModule> affectedModules,
        List<RiskNote> riskNotes,
        String riskLevel,
        Effort roughEffort,
        List<String> missingEvidence,
        String confidence,
        List<Evidence> evidence,
        List<SimilarPastChange> similarPastChanges,
        List<ScopeCreepFinding> scopeCreepFindings) {

    public record AffectedModule(String name, String path, String reason, String evidence) {
    }

    public record RiskNote(String note, String evidence) {
    }

    public record Effort(String estimate, String basis) {
    }

    /**
     * Only populated for PR-sourced impact analysis (see
     * CoordinatorService.impactAnalysisFromPr / ScopeCreepDetector) -- free
     * text change requests have no actual diff to compare declared modules
     * against, so this stays empty for that path.
     */
    public record ScopeCreepFinding(String path, String kind, String detail) {
    }
}
