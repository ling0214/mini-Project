package com.miniproject.backend.skills;

import com.miniproject.backend.artifact.Evidence;

import java.util.List;

/** Output shape from skills/impact-analysis.md. */
public record ImpactAnalysisResult(
        List<AffectedModule> affectedModules,
        List<RiskNote> riskNotes,
        String riskLevel,
        Effort roughEffort,
        List<String> missingEvidence,
        String confidence,
        List<Evidence> evidence) {

    public record AffectedModule(String name, String path, String reason, String evidence) {
    }

    public record RiskNote(String note, String evidence) {
    }

    public record Effort(String estimate, String basis) {
    }
}
