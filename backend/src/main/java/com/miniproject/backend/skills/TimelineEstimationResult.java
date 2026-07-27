package com.miniproject.backend.skills;

import com.miniproject.backend.artifact.Evidence;

import java.util.List;

/** Output shape for the timeline-estimation skill (docs/proposal.md Section 5.8). */
public record TimelineEstimationResult(
        String estimatedTimeline,
        Breakdown breakdown,
        List<String> basis,
        List<String> assumptions,
        String confidence,
        List<Evidence> evidence) {

    public record Breakdown(
            String development,
            String unitTesting,
            String qaRegression,
            String reviewUat,
            String riskBuffer) {
    }
}
