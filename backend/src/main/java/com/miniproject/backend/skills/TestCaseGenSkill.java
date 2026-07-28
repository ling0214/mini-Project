package com.miniproject.backend.skills;

import com.miniproject.backend.mcp.ProjectGraphClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
    private final String targetProjectName;

    @Autowired
    public TestCaseGenSkill(
            ProjectGraphClient graphClient,
            TestCaseGenSynthesizer synthesizer,
            @Value("${analysis.target-project.name:MyBanjirCare}") String targetProjectName) {
        this.graphClient = graphClient;
        this.synthesizer = synthesizer;
        this.targetProjectName = targetProjectName;
    }

    TestCaseGenSkill(ProjectGraphClient graphClient, TestCaseGenSynthesizer synthesizer) {
        this(graphClient, synthesizer, "MyBanjirCare");
    }

    @SuppressWarnings("unchecked")
    public TestCaseGenResult run(String target) {
        Map<String, Object> targetInfo = graphClient.getEndpointInfo(target);
        if (!Boolean.TRUE.equals(targetInfo.get("found"))) {
            targetInfo = targetInfoFromProjectContext(target);
            if (!Boolean.TRUE.equals(targetInfo.get("found"))) {
                return synthesizer.synthesize(target, targetInfo, Map.of(), Map.of(), Map.of());
            }
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> targetInfoFromProjectContext(String target) {
        try {
            Map<String, Object> context = graphClient.searchProjectContext(targetProjectName, target, 1);
            Object matches = context.get("matches");
            if (matches instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Map<?, ?> first) {
                Map<String, Object> match = (Map<String, Object>) first;
                return Map.of(
                        "found", true,
                        "name", String.valueOf(match.getOrDefault("name", target)),
                        "file", String.valueOf(match.getOrDefault("file", targetProjectName)),
                        "line", match.getOrDefault("line", 1),
                        "calls", List.of(),
                        "called_by", List.of());
            }
        } catch (RuntimeException ignored) {
            return Map.of("found", false, "name", target);
        }
        return Map.of("found", false, "name", target);
    }
}
