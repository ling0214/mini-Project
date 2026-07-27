package com.miniproject.backend.skills;

import java.util.Map;

/**
 * Turns grounded facts already retrieved (get_endpoint_info for the target
 * and each of its calls/callers, get_test_coverage, search_issues) into a
 * draft test sheet. Same seam as {@link AnswerSynthesizer} — a Claude-backed
 * version is a future swap-in behind this interface, not a rewrite of
 * {@link TestCaseGenSkill}.
 */
public interface TestCaseGenSynthesizer {

    TestCaseGenResult synthesize(
            String target,
            Map<String, Object> targetInfo,
            Map<String, Map<String, Object>> relatedInfo,
            Map<String, Object> coverage,
            Map<String, Object> issueSearch);
}
