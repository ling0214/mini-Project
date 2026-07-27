package com.miniproject.backend.web;

import com.miniproject.backend.skills.RequirementAnalysisResult;

/**
 * Derived, not persisted: whether a requirement-analysis result still has
 * open questions the analyst needs to answer before this can go to review.
 */
public enum AnalysisStatus {
    NEEDS_CLARIFICATION,
    READY_FOR_REVIEW;

    public static AnalysisStatus from(RequirementAnalysisResult result) {
        return result.missingInformation().isEmpty() ? READY_FOR_REVIEW : NEEDS_CLARIFICATION;
    }
}
