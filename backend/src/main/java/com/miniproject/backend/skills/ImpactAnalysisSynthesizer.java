package com.miniproject.backend.skills;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns grounded facts already retrieved from the project graph (trace_impact
 * per candidate entry point, plus a search_issues call over the whole change
 * request) into a final impact analysis. Same seam as {@link AnswerSynthesizer}
 * in {@link CodeQaSkill} — a Claude-backed implementation is a future swap-in
 * behind this interface, not a rewrite of {@link ImpactAnalysisSkill}.
 */
public interface ImpactAnalysisSynthesizer {

    ImpactAnalysisResult synthesize(
            String changeRequest,
            Set<String> candidates,
            List<Map<String, Object>> traces,
            Map<String, Object> issueSearch);
}
