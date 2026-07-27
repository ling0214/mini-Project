package com.miniproject.backend.skills;

import com.miniproject.backend.artifact.Evidence;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Week 3 default: assembles a draft test sheet purely from graph facts
 * (get_endpoint_info, get_test_coverage, search_issues) — no LLM call, same
 * "rule-based first" position as the other synthesizers. Every case's
 * rationale cites the exact call-graph edge or issue it came from; nothing
 * is generated for a target the graph couldn't resolve (skills/test-case-gen.md
 * rule 4).
 */
@Component
public class RuleBasedTestCaseGenSynthesizer implements TestCaseGenSynthesizer {

    @Override
    @SuppressWarnings("unchecked")
    public TestCaseGenResult synthesize(
            String target,
            Map<String, Object> targetInfo,
            Map<String, Map<String, Object>> relatedInfo,
            Map<String, Object> coverage,
            Map<String, Object> issueSearch) {

        if (!Boolean.TRUE.equals(targetInfo.get("found"))) {
            return new TestCaseGenResult(target, List.of(), List.of(), List.of(),
                    List.of("Target '" + target + "' not found in the project graph — insufficient graph coverage, no test cases generated."),
                    "low", List.of());
        }

        String targetSource = targetInfo.get("file") + ":" + targetInfo.get("line");
        List<Evidence> evidence = new ArrayList<>();
        List<TestCaseGenResult.TestCase> cases = new ArrayList<>();
        int n = 1;

        List<Map<String, Object>> coveredBy = (List<Map<String, Object>>) coverage.getOrDefault("covered_by", List.of());
        List<String> existingCoverage = new ArrayList<>();
        for (Map<String, Object> c : coveredBy) {
            String testName = String.valueOf(c.get("test"));
            String src = c.get("file") + ":" + c.get("line");
            existingCoverage.add(testName + " (" + src + ")");
            evidence.add(new Evidence(testName + " already covers " + target, src));
        }

        List<Object> calls = (List<Object>) targetInfo.getOrDefault("calls", List.of());
        for (Object callObj : calls) {
            String call = String.valueOf(callObj);
            String src = sourceOf(relatedInfo.get(call), targetSource);
            String rationale = target + " calls " + call + " (" + src + ")";
            cases.add(new TestCaseGenResult.TestCase("P" + n++, "positive",
                    "Call " + target + " with valid input that exercises the " + call + " path",
                    call + " is invoked without error", rationale, src));
            evidence.add(new Evidence(rationale, src));
        }
        if (calls.isEmpty()) {
            cases.add(new TestCaseGenResult.TestCase("P" + n++, "positive",
                    "Call " + target + " with valid input",
                    target + " completes without error",
                    target + " has no resolved downstream calls in the graph (leaf function)", targetSource));
        }

        List<Object> calledBy = (List<Object>) targetInfo.getOrDefault("called_by", List.of());
        for (Object callerObj : calledBy) {
            String caller = String.valueOf(callerObj);
            String src = sourceOf(relatedInfo.get(caller), targetSource);
            String rationale = caller + " calls " + target + " (" + src + ") — reproduce that call path";
            cases.add(new TestCaseGenResult.TestCase("E" + n++, "edge",
                    "Call " + target + " the same way " + caller + " does",
                    "Behaviour still matches what " + caller + " expects from " + target,
                    rationale, src));
            evidence.add(new Evidence(rationale, src));
        }

        String negativeRationale = target + " (" + targetSource + ") is the entry point — input-validation branches "
                + "aren't visible to a static call graph, only that this is where invalid input first arrives";
        cases.add(new TestCaseGenResult.TestCase("N" + n, "negative",
                "Call " + target + " with invalid or missing input",
                target + " fails safely (exact error path not resolved by static call-graph analysis — confirm against source)",
                negativeRationale, targetSource));
        evidence.add(new Evidence(target + " entry point", targetSource));

        List<String> regressionChecklist = new ArrayList<>();
        List<Map<String, Object>> issueMatches = (List<Map<String, Object>>) issueSearch.getOrDefault("matches", List.of());
        for (Map<String, Object> issue : issueMatches) {
            String note = "Verify '" + issue.get("title") + "' does not recur — see issue #" + issue.get("id");
            regressionChecklist.add(note);
            evidence.add(new Evidence(note, "issue #" + issue.get("id")));
        }

        List<String> missingEvidence = new ArrayList<>();
        if (calls.isEmpty() && calledBy.isEmpty() && issueMatches.isEmpty()) {
            missingEvidence.add("No downstream calls, callers, or related issues found in the graph — "
                    + "cases below are grounded only in the entry point itself.");
        }

        String confidence = !issueMatches.isEmpty() ? "high" : "medium";

        return new TestCaseGenResult(target, existingCoverage, cases, regressionChecklist, missingEvidence, confidence, evidence);
    }

    private String sourceOf(Map<String, Object> info, String fallback) {
        if (info != null && Boolean.TRUE.equals(info.get("found"))) {
            return info.get("file") + ":" + info.get("line");
        }
        return fallback;
    }
}
