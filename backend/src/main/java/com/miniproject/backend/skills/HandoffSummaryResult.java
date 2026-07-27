package com.miniproject.backend.skills;

import com.miniproject.backend.artifact.Evidence;

import java.util.List;

/** Reviewable output package for sharing Software Analyst findings. */
public record HandoffSummaryResult(
        String requirementSummary,
        List<String> businessRules,
        List<String> clarifications,
        List<String> assumptions,
        List<ImpactArea> impactAreas,
        List<RiskNote> riskNotes,
        String riskLevel,
        String effortEstimate,
        List<TestPlanSummary> testPlans,
        List<String> openQuestions,
        List<Evidence> evidence) {

    public record ImpactArea(String name, String path, String reason) {
    }

    public record RiskNote(String note, String evidence) {
    }

    public record TestPlanSummary(String target, int caseCount, List<String> regressionChecklist) {
    }
}
