package com.miniproject.backend.skills;

import com.miniproject.backend.artifact.Evidence;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Week 2 default: assembles the impact analysis from graph traces, issue
 * matches, and optional project-context matches. No LLM call is made here.
 */
@Component
public class RuleBasedImpactAnalysisSynthesizer implements ImpactAnalysisSynthesizer {

    @Override
    @SuppressWarnings("unchecked")
    public ImpactAnalysisResult synthesize(
            String changeRequest,
            Set<String> candidates,
            List<Map<String, Object>> traces,
            Map<String, Object> issueSearch) {

        List<String> missingEvidence = new ArrayList<>();
        List<String> unresolvedCandidates = new ArrayList<>();
        Map<String, ImpactAnalysisResult.AffectedModule> affectedByName = new LinkedHashMap<>();

        for (Map<String, Object> trace : traces) {
            String entryName = String.valueOf(trace.get("name"));
            if (!Boolean.TRUE.equals(trace.get("found"))) {
                unresolvedCandidates.add(entryName);
                continue;
            }

            String entrySource = trace.get("file") + ":" + trace.get("line");
            String entryReason = trace.get("reason") == null
                    ? entryName + " matched in the change request"
                    : String.valueOf(trace.get("reason"));
            affectedByName.putIfAbsent(entryName, new ImpactAnalysisResult.AffectedModule(
                    entryName, entrySource, entryReason, entrySource));

            List<Map<String, Object>> affected = (List<Map<String, Object>>) trace.getOrDefault("affected", List.of());
            for (Map<String, Object> item : affected) {
                String name = String.valueOf(item.get("name"));
                String source = item.get("file") + ":" + item.get("line");
                int hops = ((Number) item.get("hops")).intValue();
                String hopLabel = hops + " hop" + (hops == 1 ? "" : "s");
                boolean downstream = "calls".equals(item.get("relation"));
                String reason = downstream
                        ? entryName + " calls " + name + " (" + hopLabel + ")"
                        : name + " calls " + entryName + " (" + hopLabel + ")";

                affectedByName.putIfAbsent(name, new ImpactAnalysisResult.AffectedModule(name, source, reason, source));
            }
        }

        if (candidates.isEmpty()) {
            missingEvidence.add("No identifier in the change request resolved against the project graph.");
        } else if (!unresolvedCandidates.isEmpty()) {
            missingEvidence.add(unresolvedCandidates.size() + " candidate word(s) did not resolve in the project graph ("
                    + String.join(", ", unresolvedCandidates) + ") - insufficient graph coverage for those identifiers.");
        }

        List<ImpactAnalysisResult.AffectedModule> affectedModules = List.copyOf(affectedByName.values());

        List<ImpactAnalysisResult.RiskNote> riskNotes = new ArrayList<>();
        List<Map<String, Object>> issueMatches = (List<Map<String, Object>>) issueSearch.getOrDefault("matches", List.of());
        for (Map<String, Object> issue : issueMatches) {
            String note = "#" + issue.get("id") + " (" + issue.get("state") + ") " + issue.get("title");
            riskNotes.add(new ImpactAnalysisResult.RiskNote(note, "issue #" + issue.get("id")));
        }

        String riskLevel = riskNotes.isEmpty() ? "low" : riskNotes.size() <= 2 ? "medium" : "elevated";

        String estimate = affectedModules.size() <= 2 ? "S" : affectedModules.size() <= 6 ? "M" : "L";
        ImpactAnalysisResult.Effort effort = new ImpactAnalysisResult.Effort(
                estimate, affectedModules.size() + " affected module(s) in the graph blast radius");

        String confidence = affectedModules.isEmpty() ? "low" : riskNotes.isEmpty() ? "medium" : "high";

        List<Evidence> evidence = new ArrayList<>();
        for (ImpactAnalysisResult.AffectedModule m : affectedModules) {
            evidence.add(new Evidence(m.reason(), m.evidence()));
        }
        for (ImpactAnalysisResult.RiskNote r : riskNotes) {
            evidence.add(new Evidence(r.note(), r.evidence()));
        }

        return new ImpactAnalysisResult(
                affectedModules, riskNotes, riskLevel, effort, missingEvidence, confidence, evidence, List.of());
    }
}
