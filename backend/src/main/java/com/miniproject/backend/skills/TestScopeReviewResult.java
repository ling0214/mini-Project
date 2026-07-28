package com.miniproject.backend.skills;

import com.miniproject.backend.artifact.Evidence;

import java.util.List;

/** Analyst-reviewed testing scope derived from a generated test-case artifact. */
public record TestScopeReviewResult(
        String target,
        List<ManagedTestCase> cases,
        int acceptedCount,
        int rejectedCount,
        int backlogCount,
        List<String> regressionChecklist,
        String notes,
        String readiness,
        List<Evidence> evidence) {

    public record ManagedTestCase(
            String id,
            String type,
            String input,
            String expected,
            String rationale,
            String evidence,
            String status,
            String priority) {
    }
}
