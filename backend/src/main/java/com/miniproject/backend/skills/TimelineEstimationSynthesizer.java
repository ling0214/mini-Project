package com.miniproject.backend.skills;

import java.util.List;
import java.util.Map;

/**
 * Turns a reviewed impact-analysis result — plus any test-case-gen artifacts
 * already handed off from it (Section 5.7) — into a rule-based timeline
 * estimate. No MCP tool calls: unlike the other skills, this one derives
 * everything from artifacts already fetched by CoordinatorService, which is
 * why there's no separate "Skill" orchestration class, only this seam.
 */
public interface TimelineEstimationSynthesizer {

    TimelineEstimationResult synthesize(
            String sourceTaskId,
            Map<String, Object> impactResult,
            List<ChildArtifact> childTestCaseGenArtifacts,
            TimelineAssumptions assumptions);

    record ChildArtifact(String taskId, Map<String, Object> result) {
    }

    record TimelineAssumptions(Integer developers, Boolean testersAvailable) {

        public int developersOrDefault() {
            return (developers == null || developers < 1) ? 1 : developers;
        }

        public boolean testersAvailableOrDefault() {
            return testersAvailable == null || testersAvailable;
        }
    }
}
