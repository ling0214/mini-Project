package com.miniproject.backend.skills;

import com.miniproject.backend.artifact.Evidence;

import java.util.List;

/** Output shape from skills/test-case-gen.md. */
public record TestCaseGenResult(
        String target,
        List<String> existingCoverage,
        List<TestCase> cases,
        List<String> regressionChecklist,
        List<String> missingEvidence,
        String confidence,
        List<Evidence> evidence) {

    public record TestCase(String id, String type, String input, String expected, String rationale, String evidence) {
    }
}
