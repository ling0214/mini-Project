package com.miniproject.backend.skills;

import java.util.List;
import java.util.Map;

/**
 * Turns grounded facts already retrieved from the project graph into a
 * final answer. Deliberately separate from the tool-calling step in
 * {@link CodeQaSkill} — swapping the rule-based Week 1 implementation for a
 * Claude-backed one later is a change to this seam only.
 */
public interface AnswerSynthesizer {

    CodeQaResult synthesize(String question, List<Map<String, Object>> resolvedEndpoints, Map<String, Object> issueSearch);
}
