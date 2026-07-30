package com.miniproject.backend.coordinator;

import com.miniproject.backend.skills.RequirementAnalysisResult;

import java.util.List;

/** One round in a requirement-analysis clarify chain (CoordinatorService.clarificationHistory). */
public record ClarificationHistoryEntry(
        String taskId,
        String createdAt,
        boolean reviewed,
        List<String> missingInformation,
        List<RequirementAnalysisResult.Ambiguity> ambiguities,
        String clarificationAnswered) {
}
