package com.miniproject.backend.coordinator;

import com.miniproject.backend.agent.AgentRegistry;
import com.miniproject.backend.artifact.Artifact;
import com.miniproject.backend.artifact.Evidence;
import com.miniproject.backend.github.GitHubPrReader;
import com.miniproject.backend.persistence.ArtifactPersistenceService;
import com.miniproject.backend.skills.CodeQaResult;
import com.miniproject.backend.skills.CodeQaSkill;
import com.miniproject.backend.skills.ImpactAnalysisResult;
import com.miniproject.backend.skills.ImpactAnalysisSkill;
import com.miniproject.backend.skills.TestCaseGenResult;
import com.miniproject.backend.skills.TestCaseGenSkill;
import com.miniproject.backend.skills.TimelineEstimationResult;
import com.miniproject.backend.skills.TimelineEstimationSynthesizer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * agents/coordinator-agent.md: deterministic routing only. The active
 * profile is asserted by the caller (the UI's profile switcher), not
 * guessed by an LLM — see docs/proposal.md Section 5.7 for why this stays
 * deterministic (no autonomous skill-selection) until an LLM planner exists.
 * Permission checks are delegated to {@link AgentRegistry} — a bounded
 * {@link com.miniproject.backend.agent.Agent} per profile — rather than a
 * static map, so "which skills can this role use" lives in one small class
 * per role instead of one map literal here.
 */
@Service
public class CoordinatorService {

    private final CodeQaSkill codeQaSkill;
    private final ImpactAnalysisSkill impactAnalysisSkill;
    private final GitHubPrReader prReader;
    private final TestCaseGenSkill testCaseGenSkill;
    private final TimelineEstimationSynthesizer timelineEstimationSynthesizer;
    private final ArtifactPersistenceService persistence;
    private final AgentRegistry agentRegistry;

    public CoordinatorService(
            CodeQaSkill codeQaSkill,
            ImpactAnalysisSkill impactAnalysisSkill,
            GitHubPrReader prReader,
            TestCaseGenSkill testCaseGenSkill,
            TimelineEstimationSynthesizer timelineEstimationSynthesizer,
            ArtifactPersistenceService persistence,
            AgentRegistry agentRegistry) {
        this.codeQaSkill = codeQaSkill;
        this.impactAnalysisSkill = impactAnalysisSkill;
        this.prReader = prReader;
        this.testCaseGenSkill = testCaseGenSkill;
        this.timelineEstimationSynthesizer = timelineEstimationSynthesizer;
        this.persistence = persistence;
        this.agentRegistry = agentRegistry;
    }

    public Artifact<CodeQaResult> codeQa(String profile, String question) {
        requireSkillAllowed(profile, "code-qa");
        CodeQaResult result = codeQaSkill.run(question);
        Artifact<CodeQaResult> artifact = Artifact.draft(profile + "-agent", "code-qa", result, result.evidence());
        persistence.save(artifact, profile, question);
        return artifact;
    }

    public Artifact<ImpactAnalysisResult> impactAnalysis(String profile, String changeRequest) {
        requireSkillAllowed(profile, "impact-analysis");
        ImpactAnalysisResult result = impactAnalysisSkill.run(changeRequest);
        Artifact<ImpactAnalysisResult> artifact = Artifact.draft(profile + "-agent", "impact-analysis", result, result.evidence());
        persistence.save(artifact, profile, changeRequest);
        return artifact;
    }

    /**
     * Same skill, alternate input source: reads a public GitHub PR (title +
     * changed-file patches, GET-only, no write-back — see GitHubPrReader)
     * and feeds it through the same ImpactAnalysisSkill/report as free-text
     * change requests, adding one extra evidence entry for the PR itself.
     */
    public Artifact<ImpactAnalysisResult> impactAnalysisFromPr(String profile, String prUrl) {
        requireSkillAllowed(profile, "impact-analysis");
        GitHubPrReader.PrSummary pr = prReader.fetch(prUrl);
        String changeRequestText = prReader.toChangeRequestText(pr);
        ImpactAnalysisResult result = impactAnalysisSkill.run(changeRequestText);

        List<Evidence> evidence = new ArrayList<>(result.evidence());
        evidence.add(new Evidence("Source PR: " + pr.title(), prUrl));
        ImpactAnalysisResult withPrEvidence = new ImpactAnalysisResult(
                result.affectedModules(), result.riskNotes(), result.riskLevel(), result.roughEffort(),
                result.missingEvidence(), result.confidence(), evidence);

        Artifact<ImpactAnalysisResult> artifact =
                Artifact.draft(profile + "-agent", "impact-analysis", withPrEvidence, withPrEvidence.evidence());
        persistence.save(artifact, profile, prUrl);
        return artifact;
    }

    public Artifact<TestCaseGenResult> testCaseGen(String profile, String target) {
        return testCaseGen(profile, target, null);
    }

    private Artifact<TestCaseGenResult> testCaseGen(String profile, String target, String parentTaskId) {
        requireSkillAllowed(profile, "test-case-gen");
        TestCaseGenResult result = testCaseGenSkill.run(target);
        Artifact<TestCaseGenResult> artifact = Artifact.draft(profile + "-agent", "test-case-gen", result, result.evidence());
        persistence.save(artifact, profile, target, parentTaskId);
        return artifact;
    }

    /**
     * Deterministic handoff: a Tester Agent picking up a reviewed Project
     * Analyst / Business Analyst impact-analysis artifact and running
     * test-case-gen against one of its affected modules — Example 2 in
     * agents/coordinator-agent.md. Gated on the source artifact being both
     * the right skill and already reviewed, so test generation is always
     * grounded in an analysis a human has actually accepted, not typed
     * freely against an unreviewed (possibly wrong) blast radius.
     */
    public Artifact<TestCaseGenResult> handoffToTestCaseGen(String sourceTaskId, String profile, String target) {
        requireSkillAllowed(profile, "test-case-gen");

        Artifact<Object> source = persistence.findArtifact(sourceTaskId)
                .orElseThrow(() -> new IllegalArgumentException("No artifact found for task_id " + sourceTaskId));
        if (!"impact-analysis".equals(source.skill())) {
            throw new IllegalArgumentException(
                    "Handoff to test-case-gen requires an impact-analysis source artifact, got: " + source.skill());
        }
        if (!source.reviewed()) {
            throw new IllegalArgumentException(
                    "Source artifact " + sourceTaskId + " must be reviewed before handoff to another agent");
        }

        Set<String> affectedNames = new LinkedHashSet<>();
        if (source.result() instanceof Map<?, ?> resultMap && resultMap.get("affected_modules") instanceof List<?> modules) {
            for (Object m : modules) {
                if (m instanceof Map<?, ?> module && module.get("name") != null) {
                    affectedNames.add(String.valueOf(module.get("name")));
                }
            }
        }
        if (!affectedNames.contains(target)) {
            throw new IllegalArgumentException(
                    "'" + target + "' is not one of the affected modules in artifact " + sourceTaskId + ": " + affectedNames);
        }

        return testCaseGen(profile, target, sourceTaskId);
    }

    /**
     * Deterministic handoff, Phase 5: a Project Analyst turning a reviewed
     * impact-analysis artifact into a rule-based timeline estimate. If a
     * test-case-gen artifact has already been handed off from the same
     * source (Section 5.7 lineage), its real case count grounds the QA
     * estimate instead of a rough per-module guess — see
     * RuleBasedTimelineEstimationSynthesizer.
     */
    @SuppressWarnings("unchecked")
    public Artifact<TimelineEstimationResult> handoffToTimelineEstimation(
            String sourceTaskId, String profile, Integer developers, Boolean testersAvailable) {
        requireSkillAllowed(profile, "timeline-estimation");

        Artifact<Object> source = persistence.findArtifact(sourceTaskId)
                .orElseThrow(() -> new IllegalArgumentException("No artifact found for task_id " + sourceTaskId));
        if (!"impact-analysis".equals(source.skill())) {
            throw new IllegalArgumentException(
                    "Timeline estimation requires an impact-analysis source artifact, got: " + source.skill());
        }
        if (!source.reviewed()) {
            throw new IllegalArgumentException(
                    "Source artifact " + sourceTaskId + " must be reviewed before generating a timeline estimate");
        }
        if (!(source.result() instanceof Map<?, ?> resultMap)) {
            throw new IllegalStateException("Malformed impact-analysis result for artifact " + sourceTaskId);
        }

        List<TimelineEstimationSynthesizer.ChildArtifact> children = persistence.findChildren(sourceTaskId).stream()
                .filter(child -> "test-case-gen".equals(child.skill()))
                .filter(child -> child.result() instanceof Map<?, ?>)
                .map(child -> new TimelineEstimationSynthesizer.ChildArtifact(
                        child.taskId(), (Map<String, Object>) child.result()))
                .toList();

        TimelineEstimationSynthesizer.TimelineAssumptions assumptions =
                new TimelineEstimationSynthesizer.TimelineAssumptions(developers, testersAvailable);
        TimelineEstimationResult result = timelineEstimationSynthesizer.synthesize(
                sourceTaskId, (Map<String, Object>) resultMap, children, assumptions);

        Artifact<TimelineEstimationResult> artifact =
                Artifact.draft(profile + "-agent", "timeline-estimation", result, result.evidence());
        persistence.save(artifact, profile, "Timeline estimate for " + sourceTaskId, sourceTaskId);
        return artifact;
    }

    private void requireSkillAllowed(String profile, String skill) {
        if (!agentRegistry.require(profile).allowedSkills().contains(skill)) {
            throw new IllegalArgumentException("Profile '" + profile + "' cannot use skill '" + skill + "'");
        }
    }
}
