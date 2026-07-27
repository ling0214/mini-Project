package com.miniproject.backend.skills;

import com.miniproject.backend.mcp.ProjectGraphClient;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * skills/test-case-gen.md: resolve the target via get_endpoint_info, look up
 * each call/caller too (so cases can cite an exact file:line, not just a
 * name), check get_test_coverage so cases fill gaps instead of duplicating
 * existing tests, and search_issues for regression-checklist grounding. If
 * the target doesn't resolve, stop — handled by the synthesizer per rule 4,
 * not by generating anything ungrounded here.
 */
@Component
public class TestCaseGenSkill {

    private final ProjectGraphClient graphClient;
    private final TestCaseGenSynthesizer synthesizer;

    public TestCaseGenSkill(ProjectGraphClient graphClient, TestCaseGenSynthesizer synthesizer) {
        this.graphClient = graphClient;
        this.synthesizer = synthesizer;
    }

    @SuppressWarnings("unchecked")
    public TestCaseGenResult run(String target) {
        Map<String, Object> targetInfo = graphClient.getEndpointInfo(target);
        if (!Boolean.TRUE.equals(targetInfo.get("found"))) {
            return synthesizer.synthesize(target, targetInfo, Map.of(), Map.of(), Map.of());
        }

        Map<String, Map<String, Object>> relatedInfo = new LinkedHashMap<>();
        for (Object call : (List<Object>) targetInfo.getOrDefault("calls", List.of())) {
            String name = String.valueOf(call);
            relatedInfo.put(name, graphClient.getEndpointInfo(name));
        }
        for (Object caller : (List<Object>) targetInfo.getOrDefault("called_by", List.of())) {
            String name = String.valueOf(caller);
            relatedInfo.putIfAbsent(name, graphClient.getEndpointInfo(name));
        }

        Map<String, Object> coverage = graphClient.getTestCoverage(target);
        Map<String, Object> issueSearch = graphClient.searchIssues(target);

        return synthesizer.synthesize(target, targetInfo, relatedInfo, coverage, issueSearch);
    }
}
