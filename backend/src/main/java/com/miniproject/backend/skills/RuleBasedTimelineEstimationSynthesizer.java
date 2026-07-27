package com.miniproject.backend.skills;

import com.miniproject.backend.artifact.Evidence;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Week 5 default: a fixed rule-based formula, no LLM call — same position
 * as every other synthesizer in this codebase (docs/proposal.md Chapter 8).
 * The one thing worth calling out here versus the other rule-based
 * synthesizers: this is the first one that gets *more* grounded, not just
 * more confident, when more of the pipeline has actually run — the QA
 * estimate uses a real generated case count when a test-case-gen artifact
 * has been handed off from the same impact-analysis, and falls back to a
 * clearly-labelled rough assumption otherwise.
 */
@Component
public class RuleBasedTimelineEstimationSynthesizer implements TimelineEstimationSynthesizer {

    private static final double DEV_DAYS_PER_MODULE = 0.5;
    private static final double UNIT_TEST_DAYS_PER_MODULE = 0.25;
    private static final double QA_DAYS_PER_CASE = 0.25;
    private static final double REVIEW_UAT_DAYS = 1.0;

    @Override
    @SuppressWarnings("unchecked")
    public TimelineEstimationResult synthesize(
            String sourceTaskId,
            Map<String, Object> impactResult,
            List<ChildArtifact> childTestCaseGenArtifacts,
            TimelineAssumptions assumptions) {

        List<Map<String, Object>> affectedModules =
                (List<Map<String, Object>>) impactResult.getOrDefault("affected_modules", List.of());
        List<Map<String, Object>> riskNotes =
                (List<Map<String, Object>>) impactResult.getOrDefault("risk_notes", List.of());
        String riskLevel = String.valueOf(impactResult.getOrDefault("risk_level", "medium"));

        boolean caseCountGrounded = !childTestCaseGenArtifacts.isEmpty();
        int caseCount = 0;
        List<Evidence> evidence = new ArrayList<>();
        evidence.add(new Evidence("Affected-module count and risk level", "artifact " + sourceTaskId));
        if (caseCountGrounded) {
            for (ChildArtifact child : childTestCaseGenArtifacts) {
                List<Object> cases = (List<Object>) child.result().getOrDefault("cases", List.of());
                caseCount += cases.size();
                evidence.add(new Evidence("Generated regression case count", "artifact " + child.taskId()));
            }
        } else {
            caseCount = affectedModules.size();
        }

        int developers = assumptions.developersOrDefault();
        boolean testersAvailable = assumptions.testersAvailableOrDefault();

        double developmentDays = round(affectedModules.size() * DEV_DAYS_PER_MODULE / developers);
        double unitTestingDays = round(affectedModules.size() * UNIT_TEST_DAYS_PER_MODULE);
        double qaRegressionDays = round(caseCount * QA_DAYS_PER_CASE);
        double riskBufferDays = riskBufferFor(riskLevel);
        double reviewUatDays = REVIEW_UAT_DAYS;

        double totalLow = developmentDays + unitTestingDays + qaRegressionDays + reviewUatDays;
        double totalHigh = totalLow + riskBufferDays;

        TimelineEstimationResult.Breakdown breakdown = new TimelineEstimationResult.Breakdown(
                formatDays(developmentDays), formatDays(unitTestingDays), formatDays(qaRegressionDays),
                formatDays(reviewUatDays), formatDays(riskBufferDays));

        List<String> basis = new ArrayList<>();
        basis.add(affectedModules.size() + " affected module(s) from impact-analysis artifact " + sourceTaskId);
        basis.add(riskLevel + " risk level (" + riskNotes.size() + " related historical issue(s))");
        basis.add(caseCount + (caseCountGrounded
                ? " generated regression case(s) from " + childTestCaseGenArtifacts.size() + " test-case-gen handoff artifact(s)"
                : " estimated regression case(s) — no test-case-gen artifact handed off yet, this is a rough 1-per-module assumption"));

        List<String> assumptionNotes = new ArrayList<>();
        assumptionNotes.add(developers + " developer(s) assumed");
        assumptionNotes.add(testersAvailable
                ? "A tester is assumed available for QA regression"
                : "No tester currently available — qa_regression may need to extend beyond this estimate");
        assumptionNotes.add("No database migration or third-party API contract change assumed unless already noted in risk notes above");

        String confidence = caseCountGrounded && !riskNotes.isEmpty() ? "high"
                : (caseCountGrounded || !riskNotes.isEmpty()) ? "medium" : "low";

        return new TimelineEstimationResult(
                formatDays(totalLow) + "-" + formatDays(totalHigh) + " working days",
                breakdown, basis, assumptionNotes, confidence, evidence);
    }

    private double riskBufferFor(String riskLevel) {
        return switch (riskLevel.toLowerCase(Locale.ROOT)) {
            case "low" -> 0.5;
            case "elevated" -> 1.5;
            default -> 1.0; // medium, or unrecognised
        };
    }

    private double round(double value) {
        return Math.round(value * 4) / 4.0; // nearest quarter day
    }

    private String formatDays(double days) {
        return days == Math.floor(days) ? String.valueOf((int) days) : String.valueOf(days);
    }
}
