package com.miniproject.backend.coordinator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniproject.backend.agent.AgentRegistry;
import com.miniproject.backend.artifact.Artifact;
import com.miniproject.backend.artifact.Evidence;
import com.miniproject.backend.github.GitHubPrReader;
import com.miniproject.backend.github.ScopeCreepDetector;
import com.miniproject.backend.memory.MemoryCardService;
import com.miniproject.backend.memory.SimilarPastChange;
import com.miniproject.backend.persistence.ArtifactPersistenceService;
import com.miniproject.backend.skills.CodeQaResult;
import com.miniproject.backend.skills.CodeQaSkill;
import com.miniproject.backend.skills.HandoffSummaryResult;
import com.miniproject.backend.skills.HermesSetupWizardAnswers;
import com.miniproject.backend.skills.HermesSetupWizardResult;
import com.miniproject.backend.skills.HermesSetupWizardSkill;
import com.miniproject.backend.skills.HermesTrendingDigestResult;
import com.miniproject.backend.skills.HermesTrendingDigestSkill;
import com.miniproject.backend.skills.HermesVersionAdvisorResult;
import com.miniproject.backend.skills.HermesVersionAdvisorSkill;
import com.miniproject.backend.skills.ImpactAnalysisResult;
import com.miniproject.backend.skills.ImpactAnalysisSkill;
import com.miniproject.backend.skills.RequirementAnalysisResult;
import com.miniproject.backend.skills.RequirementAnalysisSkill;
import com.miniproject.backend.skills.TestCaseGenResult;
import com.miniproject.backend.skills.TestCaseGenSkill;
import com.miniproject.backend.skills.TestScopeReviewResult;
import com.miniproject.backend.skills.TimelineEstimationResult;
import com.miniproject.backend.skills.TimelineEstimationSynthesizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    private static final Logger REVIEW_OPPORTUNITY_LOG = LoggerFactory.getLogger(CoordinatorService.class);

    private final CodeQaSkill codeQaSkill;
    private final ImpactAnalysisSkill impactAnalysisSkill;
    private final GitHubPrReader prReader;
    private final ScopeCreepDetector scopeCreepDetector;
    private final TestCaseGenSkill testCaseGenSkill;
    private final TimelineEstimationSynthesizer timelineEstimationSynthesizer;
    private final RequirementAnalysisSkill requirementAnalysisSkill;
    private final HermesSetupWizardSkill hermesSetupWizardSkill;
    private final HermesVersionAdvisorSkill hermesVersionAdvisorSkill;
    private final HermesTrendingDigestSkill hermesTrendingDigestSkill;
    private final ArtifactPersistenceService persistence;
    private final AgentRegistry agentRegistry;
    private final ObjectMapper objectMapper;
    private final MemoryCardService memoryCardService;

    public CoordinatorService(
            CodeQaSkill codeQaSkill,
            ImpactAnalysisSkill impactAnalysisSkill,
            GitHubPrReader prReader,
            ScopeCreepDetector scopeCreepDetector,
            TestCaseGenSkill testCaseGenSkill,
            TimelineEstimationSynthesizer timelineEstimationSynthesizer,
            RequirementAnalysisSkill requirementAnalysisSkill,
            HermesSetupWizardSkill hermesSetupWizardSkill,
            HermesVersionAdvisorSkill hermesVersionAdvisorSkill,
            HermesTrendingDigestSkill hermesTrendingDigestSkill,
            ArtifactPersistenceService persistence,
            AgentRegistry agentRegistry,
            ObjectMapper objectMapper,
            MemoryCardService memoryCardService) {
        this.codeQaSkill = codeQaSkill;
        this.impactAnalysisSkill = impactAnalysisSkill;
        this.prReader = prReader;
        this.scopeCreepDetector = scopeCreepDetector;
        this.testCaseGenSkill = testCaseGenSkill;
        this.timelineEstimationSynthesizer = timelineEstimationSynthesizer;
        this.requirementAnalysisSkill = requirementAnalysisSkill;
        this.hermesSetupWizardSkill = hermesSetupWizardSkill;
        this.hermesVersionAdvisorSkill = hermesVersionAdvisorSkill;
        this.hermesTrendingDigestSkill = hermesTrendingDigestSkill;
        this.persistence = persistence;
        this.agentRegistry = agentRegistry;
        this.objectMapper = objectMapper;
        this.memoryCardService = memoryCardService;
    }

    private RequirementAnalysisResult withSimilarPastChanges(RequirementAnalysisResult result, String queryText) {
        List<SimilarPastChange> similar = memoryCardService.findSimilar("requirement-analysis", queryText, null);
        return new RequirementAnalysisResult(
                result.businessRules(), result.businessValue(), result.scopeBoundary(), result.ambiguities(),
                result.missingInformation(), result.assumptions(), result.analystConcerns(), result.projectRisks(),
                result.potentialAffectedAreas(), result.confidence(), result.evidence(), similar);
    }

    private ImpactAnalysisResult withSimilarPastChanges(ImpactAnalysisResult result, String queryText) {
        List<SimilarPastChange> similar = memoryCardService.findSimilar("impact-analysis", queryText, null);
        return new ImpactAnalysisResult(
                result.affectedModules(), result.riskNotes(), result.riskLevel(), result.roughEffort(),
                result.missingEvidence(), result.confidence(), result.evidence(), similar, result.scopeCreepFindings());
    }

    public Artifact<CodeQaResult> codeQa(String profile, String question) {
        requireSkillAllowed(profile, "code-qa");
        CodeQaResult result = codeQaSkill.run(question);
        Artifact<CodeQaResult> artifact = Artifact.draft(profile + "-agent", "code-qa", result, result.evidence());
        persistence.save(artifact, profile, question);
        logSkillReviewOpportunity("code-qa", artifact.taskId());
        return artifact;
    }

    public Artifact<ImpactAnalysisResult> impactAnalysis(String profile, String changeRequest) {
        requireSkillAllowed(profile, "impact-analysis");
        ImpactAnalysisResult result = withSimilarPastChanges(impactAnalysisSkill.run(changeRequest), changeRequest);
        Artifact<ImpactAnalysisResult> artifact = Artifact.draft(profile + "-agent", "impact-analysis", result, result.evidence());
        persistence.save(artifact, profile, changeRequest);
        logSkillReviewOpportunity("impact-analysis", artifact.taskId());
        return artifact;
    }

    /**
     * Same skill, alternate input source: reads a public GitHub PR (title +
     * changed-file patches, GET-only, no write-back — see GitHubPrReader)
     * and feeds it through the same ImpactAnalysisSkill/report as free-text
     * change requests, adding one extra evidence entry for the PR itself.
     * Also compares the PR's actually-changed files against the declared
     * affected modules (see ScopeCreepDetector) -- only possible here,
     * since this is the one impact-analysis path with a real diff to check
     * declared scope against.
     */
    public Artifact<ImpactAnalysisResult> impactAnalysisFromPr(String profile, String prUrl) {
        requireSkillAllowed(profile, "impact-analysis");
        GitHubPrReader.PrSummary pr = prReader.fetch(prUrl);
        String changeRequestText = prReader.toChangeRequestText(pr);
        ImpactAnalysisResult result = impactAnalysisSkill.run(changeRequestText);

        List<Evidence> evidence = new ArrayList<>(result.evidence());
        evidence.add(new Evidence("Source PR: " + pr.title(), prUrl));
        List<SimilarPastChange> similar = memoryCardService.findSimilar("impact-analysis", changeRequestText, null);
        List<ImpactAnalysisResult.ScopeCreepFinding> scopeCreepFindings =
                scopeCreepDetector.detect(pr.files(), result.affectedModules());
        ImpactAnalysisResult withPrEvidence = new ImpactAnalysisResult(
                result.affectedModules(), result.riskNotes(), result.riskLevel(), result.roughEffort(),
                result.missingEvidence(), result.confidence(), evidence, similar, scopeCreepFindings);

        Artifact<ImpactAnalysisResult> artifact =
                Artifact.draft(profile + "-agent", "impact-analysis", withPrEvidence, withPrEvidence.evidence());
        persistence.save(artifact, profile, prUrl);
        return artifact;
    }

    public Artifact<RequirementAnalysisResult> requirementAnalysis(String profile, String description) {
        requireSkillAllowed(profile, "requirement-analysis");
        RequirementAnalysisResult result = withSimilarPastChanges(requirementAnalysisSkill.run(description), description);
        Artifact<RequirementAnalysisResult> artifact =
                Artifact.draft(profile + "-agent", "requirement-analysis", result, result.evidence());
        persistence.save(artifact, profile, description);
        logSkillReviewOpportunity("requirement-analysis", artifact.taskId());
        return artifact;
    }

    public Artifact<HermesSetupWizardResult> hermesSetupWizard(String profile, HermesSetupWizardAnswers answers) {
        requireSkillAllowed(profile, "hermes-setup-wizard");
        HermesSetupWizardResult result = hermesSetupWizardSkill.run(answers);
        Artifact<HermesSetupWizardResult> artifact =
                Artifact.draft(profile + "-agent", "hermes-setup-wizard", result, result.evidence());
        persistence.save(artifact, profile, answers.repoPath());
        logSkillReviewOpportunity("hermes-setup-wizard", artifact.taskId());
        return artifact;
    }

    public Artifact<HermesVersionAdvisorResult> hermesVersionAdvisor(
            String profile, String repoPath, String remoteRef, String localRef, List<String> watchedPaths) {
        requireSkillAllowed(profile, "hermes-version-advisor");
        HermesVersionAdvisorResult result =
                hermesVersionAdvisorSkill.run(repoPath, remoteRef, localRef, watchedPaths);
        Artifact<HermesVersionAdvisorResult> artifact =
                Artifact.draft(profile + "-agent", "hermes-version-advisor", result, result.evidence());
        persistence.save(artifact, profile, repoPath);
        logSkillReviewOpportunity("hermes-version-advisor", artifact.taskId());
        return artifact;
    }

    /**
     * Also called by the weekly @Scheduled job (HermesTrendingDigestJob) under
     * the "software-analyst" profile — same code path as a manual trigger, so
     * there's exactly one place this runs, not a scheduled-vs-manual fork.
     */
    public Artifact<HermesTrendingDigestResult> hermesTrendingDigest(String profile) {
        requireSkillAllowed(profile, "hermes-trending-digest");
        HermesTrendingDigestResult result = hermesTrendingDigestSkill.run();
        Artifact<HermesTrendingDigestResult> artifact =
                Artifact.draft(profile + "-agent", "hermes-trending-digest", result, result.evidence());
        persistence.save(artifact, profile, "github.com/trending weekly scan");
        logSkillReviewOpportunity("hermes-trending-digest", artifact.taskId());
        return artifact;
    }

    /**
     * Unlike the other handoff methods, this deliberately does NOT require the
     * source artifact to be reviewed first — clarify exists precisely because
     * the artifact ISN'T ready for review yet (status NEEDS_CLARIFICATION).
     * Produces a new linked artifact (parentTaskId = sourceTaskId) rather than
     * mutating the original, so artifact history stays append-only.
     */
    public Artifact<RequirementAnalysisResult> clarifyRequirementAnalysis(
            String sourceTaskId, String profile, String additionalInfo) {
        requireSkillAllowed(profile, "requirement-analysis");

        Artifact<Object> source = persistence.findArtifact(sourceTaskId)
                .orElseThrow(() -> new IllegalArgumentException("No artifact found for task_id " + sourceTaskId));
        if (!"requirement-analysis".equals(source.skill())) {
            throw new IllegalArgumentException(
                    "Clarify requires a requirement-analysis source artifact, got: " + source.skill());
        }

        String originalDescription = persistence.findInputText(sourceTaskId).orElse("");
        String augmentedDescription = originalDescription + "\n\nClarifications:\n" + additionalInfo;

        RequirementAnalysisResult result =
                withSimilarPastChanges(requirementAnalysisSkill.run(augmentedDescription), augmentedDescription);
        Artifact<RequirementAnalysisResult> artifact =
                Artifact.draft(profile + "-agent", "requirement-analysis", result, result.evidence())
                        .withParentTaskId(sourceTaskId);
        persistence.save(artifact, profile, augmentedDescription, sourceTaskId);
        return artifact;
    }

    private static final String CLARIFICATION_MARKER = "\n\nClarifications:\n";

    /**
     * Turns the append-only clarify chain (clarifyRequirementAnalysis above)
     * into a readable "round N: still missing X -> analyst answered Y" view.
     * Each round's own missingInformation/ambiguities come from its persisted
     * result; what the analyst answered to produce THAT round is recovered by
     * splitting its inputText on the last CLARIFICATION_MARKER, since a round
     * born from an earlier round already contains prior markers verbatim.
     */
    public List<ClarificationHistoryEntry> clarificationHistory(String taskId) {
        Artifact<Object> latest = persistence.findArtifact(taskId)
                .orElseThrow(() -> new IllegalArgumentException("No artifact found for task_id " + taskId));
        if (!"requirement-analysis".equals(latest.skill())) {
            throw new IllegalArgumentException(
                    "Clarification history requires a requirement-analysis artifact, got: " + latest.skill());
        }

        return persistence.findClarificationChain(taskId).stream()
                .map(this::toHistoryEntry)
                .toList();
    }

    private ClarificationHistoryEntry toHistoryEntry(ArtifactPersistenceService.ChainEntry entry) {
        RequirementAnalysisResult result = readResult(entry.resultJson());
        return new ClarificationHistoryEntry(
                entry.taskId(),
                entry.createdAt().toString(),
                entry.reviewed(),
                result.missingInformation(),
                result.ambiguities(),
                clarificationAnswered(entry.inputText()));
    }

    private RequirementAnalysisResult readResult(String resultJson) {
        try {
            return objectMapper.readValue(resultJson, RequirementAnalysisResult.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt requirement-analysis result_json", e);
        }
    }

    private static String clarificationAnswered(String inputText) {
        int markerIndex = inputText == null ? -1 : inputText.lastIndexOf(CLARIFICATION_MARKER);
        return markerIndex < 0 ? "" : inputText.substring(markerIndex + CLARIFICATION_MARKER.length()).trim();
    }

    /**
     * Software Analyst workflow handoff: a reviewed requirement-analysis
     * artifact becomes the source of truth for impact-analysis. This preserves
     * clarification text because the impact input is loaded from the persisted
     * requirement artifact input, not from the UI's original text box.
     */
    public Artifact<ImpactAnalysisResult> handoffToImpactAnalysis(String sourceTaskId, String profile) {
        requireSkillAllowed(profile, "impact-analysis");

        Artifact<Object> source = persistence.findArtifact(sourceTaskId)
                .orElseThrow(() -> new IllegalArgumentException("No artifact found for task_id " + sourceTaskId));
        if (!"requirement-analysis".equals(source.skill())) {
            throw new IllegalArgumentException(
                    "Handoff to impact-analysis requires a requirement-analysis source artifact, got: " + source.skill());
        }
        if (!source.reviewed()) {
            throw new IllegalArgumentException(
                    "Source artifact " + sourceTaskId + " must be reviewed before impact analysis");
        }

        String requirementText = persistence.findInputText(sourceTaskId)
                .orElseThrow(() -> new IllegalArgumentException("No input text found for artifact " + sourceTaskId));
        ImpactAnalysisResult result = withSimilarPastChanges(impactAnalysisSkill.run(requirementText), requirementText);
        Artifact<ImpactAnalysisResult> artifact =
                Artifact.draft(profile + "-agent", "impact-analysis", result, result.evidence())
                        .withParentTaskId(sourceTaskId);
        persistence.save(artifact, profile, requirementText, sourceTaskId);
        return artifact;
    }

    public Artifact<TestCaseGenResult> testCaseGen(String profile, String target) {
        return testCaseGen(profile, target, null);
    }

    private Artifact<TestCaseGenResult> testCaseGen(String profile, String target, String parentTaskId) {
        requireSkillAllowed(profile, "test-case-gen");
        TestCaseGenResult result = testCaseGenSkill.run(target);
        Artifact<TestCaseGenResult> artifact = Artifact.draft(profile + "-agent", "test-case-gen", result, result.evidence());
        if (parentTaskId != null) {
            artifact = artifact.withParentTaskId(parentTaskId);
        }
        persistence.save(artifact, profile, target, parentTaskId);
        logSkillReviewOpportunity("test-case-gen", artifact.taskId());
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

    public Artifact<TestScopeReviewResult> reviewTestScope(
            String sourceTaskId,
            String profile,
            List<TestScopeReviewResult.ManagedTestCase> cases,
            String notes) {
        requireSkillAllowed(profile, "test-scope-review");

        Artifact<Object> source = persistence.findArtifact(sourceTaskId)
                .orElseThrow(() -> new IllegalArgumentException("No artifact found for task_id " + sourceTaskId));
        if (!"test-case-gen".equals(source.skill())) {
            throw new IllegalArgumentException(
                    "Test scope review requires a test-case-gen source artifact, got: " + source.skill());
        }
        if (cases == null || cases.isEmpty()) {
            throw new IllegalArgumentException("At least one test case is required");
        }

        Map<String, Object> sourceResult = resultMap(source, "test-case-gen");
        List<TestScopeReviewResult.ManagedTestCase> normalizedCases = cases.stream()
                .map(CoordinatorService::normalizeManagedCase)
                .toList();
        int acceptedCount = countStatus(normalizedCases, "accepted");
        int rejectedCount = countStatus(normalizedCases, "rejected");
        int backlogCount = countStatus(normalizedCases, "backlog");
        String readiness = acceptedCount > 0 ? "READY_FOR_REVIEW" : "NEEDS_TEST_CASES";

        List<Evidence> evidence = normalizedCases.stream()
                .filter(testCase -> !"rejected".equals(testCase.status()))
                .map(testCase -> new Evidence(
                        testCase.status() + " test case " + testCase.id() + ": " + testCase.expected(),
                        testCase.evidence() == null || testCase.evidence().isBlank() ? "analyst test scope" : testCase.evidence()))
                .toList();

        TestScopeReviewResult result = new TestScopeReviewResult(
                stringValue(sourceResult.get("target"), "(unknown target)"),
                normalizedCases,
                acceptedCount,
                rejectedCount,
                backlogCount,
                stringList(sourceResult, "regression_checklist"),
                notes == null ? "" : notes.trim(),
                readiness,
                evidence);

        Artifact<TestScopeReviewResult> artifact =
                Artifact.draft(profile + "-agent", "test-scope-review", result, result.evidence())
                        .withParentTaskId(sourceTaskId);
        persistence.save(artifact, profile, "Reviewed test scope for " + sourceTaskId, sourceTaskId);
        return artifact;
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
                Artifact.draft(profile + "-agent", "timeline-estimation", result, result.evidence())
                        .withParentTaskId(sourceTaskId);
        persistence.save(artifact, profile, "Timeline estimate for " + sourceTaskId, sourceTaskId);
        return artifact;
    }

    /**
     * Final Software Analyst workflow output: compile reviewed requirement and
     * impact artifacts plus generated test plans into a persisted handoff
     * summary. This makes the last workflow step auditable instead of
     * frontend-only state.
     */
    @SuppressWarnings("unchecked")
    public Artifact<HandoffSummaryResult> handoffSummary(
            String impactTaskId, String profile, String requirementTaskId, List<String> testTaskIds) {
        requireSkillAllowed(profile, "handoff-summary");

        Artifact<Object> requirement = persistence.findArtifact(requirementTaskId)
                .orElseThrow(() -> new IllegalArgumentException("No artifact found for task_id " + requirementTaskId));
        if (!"requirement-analysis".equals(requirement.skill())) {
            throw new IllegalArgumentException(
                    "Handoff summary requires a requirement-analysis artifact, got: " + requirement.skill());
        }
        if (!requirement.reviewed()) {
            throw new IllegalArgumentException(
                    "Requirement artifact " + requirementTaskId + " must be reviewed before summary generation");
        }

        Artifact<Object> impact = persistence.findArtifact(impactTaskId)
                .orElseThrow(() -> new IllegalArgumentException("No artifact found for task_id " + impactTaskId));
        if (!"impact-analysis".equals(impact.skill())) {
            throw new IllegalArgumentException(
                    "Handoff summary requires an impact-analysis source artifact, got: " + impact.skill());
        }
        if (!impact.reviewed()) {
            throw new IllegalArgumentException(
                    "Impact artifact " + impactTaskId + " must be reviewed before summary generation");
        }

        Map<String, Object> requirementResult = resultMap(requirement, "requirement-analysis");
        Map<String, Object> impactResult = resultMap(impact, "impact-analysis");
        List<Artifact<Object>> tests = findSummaryTestArtifacts(impactTaskId, testTaskIds);

        String requirementText = persistence.findInputText(requirementTaskId).orElse("");
        List<String> openQuestions = new ArrayList<>(stringList(requirementResult, "missing_information"));
        openQuestions.addAll(ambiguityNotes(requirementResult));

        HandoffSummaryResult result = new HandoffSummaryResult(
                summarizeRequirement(requirementText),
                stringList(requirementResult, "business_rules"),
                extractClarifications(requirementText),
                stringList(requirementResult, "assumptions"),
                impactAreas(impactResult),
                riskNotes(impactResult),
                stringValue(impactResult.get("risk_level"), "unknown"),
                effortEstimate(impactResult),
                testPlanSummaries(tests),
                openQuestions,
                combinedEvidence(requirement, impact, tests));

        Artifact<HandoffSummaryResult> artifact =
                Artifact.draft(profile + "-agent", "handoff-summary", result, result.evidence())
                        .withParentTaskId(impactTaskId);
        persistence.save(artifact, profile,
                "Handoff summary for requirement " + requirementTaskId + " and impact " + impactTaskId,
                impactTaskId);
        return artifact;
    }

    private List<Artifact<Object>> findSummaryTestArtifacts(String impactTaskId, List<String> testTaskIds) {
        List<Artifact<Object>> tests;
        if (testTaskIds == null || testTaskIds.isEmpty()) {
            tests = persistence.findChildren(impactTaskId).stream()
                    .filter(child -> "test-case-gen".equals(child.skill()))
                    .map(this::preferredTestScopeArtifact)
                    .toList();
        } else {
            tests = testTaskIds.stream()
                    .filter(Objects::nonNull)
                    .map(taskId -> persistence.findArtifact(taskId)
                            .orElseThrow(() -> new IllegalArgumentException("No artifact found for task_id " + taskId)))
                    .toList();
        }

        for (Artifact<Object> test : tests) {
            if (!"test-case-gen".equals(test.skill()) && !"test-scope-review".equals(test.skill())) {
                throw new IllegalArgumentException(
                        "Handoff summary test artifacts must be test-case-gen or test-scope-review, got: " + test.skill());
            }
        }
        return tests;
    }

    private Artifact<Object> preferredTestScopeArtifact(Artifact<Object> testCaseArtifact) {
        return persistence.findChildren(testCaseArtifact.taskId()).stream()
                .filter(child -> "test-scope-review".equals(child.skill()))
                .filter(Artifact::reviewed)
                .reduce((previous, current) -> current)
                .orElse(testCaseArtifact);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> resultMap(Artifact<Object> artifact, String expectedSkill) {
        if (artifact.result() instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalStateException("Malformed " + expectedSkill + " result for artifact " + artifact.taskId());
    }

    private static String summarizeRequirement(String inputText) {
        String withoutClarifications = inputText == null ? "" : inputText.split("\\R\\RClarifications:", 2)[0];
        String compact = withoutClarifications.replaceAll("\\s+", " ").trim();
        if (compact.isBlank()) {
            return "(no requirement text recorded)";
        }
        return compact.length() > 260 ? compact.substring(0, 260) + "..." : compact;
    }

    private static List<String> extractClarifications(String inputText) {
        if (inputText == null) {
            return List.of();
        }
        int index = inputText.indexOf("Clarifications:");
        if (index < 0) {
            return List.of();
        }
        return inputText.substring(index + "Clarifications:".length()).lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
    }

    private static List<String> stringList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    private static List<String> ambiguityNotes(Map<String, Object> requirementResult) {
        return mapList(requirementResult, "ambiguities").stream()
                .map(item -> stringValue(item.get("note"), ""))
                .filter(note -> !note.isBlank())
                .toList();
    }

    private static List<HandoffSummaryResult.ImpactArea> impactAreas(Map<String, Object> impactResult) {
        return mapList(impactResult, "affected_modules").stream()
                .map(item -> new HandoffSummaryResult.ImpactArea(
                        stringValue(item.get("name"), ""),
                        stringValue(item.get("path"), ""),
                        stringValue(item.get("reason"), "")))
                .toList();
    }

    private static List<HandoffSummaryResult.RiskNote> riskNotes(Map<String, Object> impactResult) {
        return mapList(impactResult, "risk_notes").stream()
                .map(item -> new HandoffSummaryResult.RiskNote(
                        stringValue(item.get("note"), ""),
                        stringValue(item.get("evidence"), "")))
                .toList();
    }

    private static String effortEstimate(Map<String, Object> impactResult) {
        Object value = impactResult.get("rough_effort");
        if (!(value instanceof Map<?, ?> effort)) {
            return "unknown";
        }
        String estimate = stringValue(effort.get("estimate"), "unknown");
        String basis = stringValue(effort.get("basis"), "");
        return basis.isBlank() ? estimate : estimate + " - " + basis;
    }

    private static List<HandoffSummaryResult.TestPlanSummary> testPlanSummaries(List<Artifact<Object>> tests) {
        return tests.stream()
                .map(test -> {
                    Map<String, Object> result = resultMap(test, "test-case-gen");
                    List<String> checklist = stringList(result, "regression_checklist");
                    int caseCount;
                    if ("test-scope-review".equals(test.skill())) {
                        caseCount = numberValue(result.get("accepted_count"), 0);
                    } else {
                        caseCount = result.get("cases") instanceof List<?> cases ? cases.size() : 0;
                    }
                    return new HandoffSummaryResult.TestPlanSummary(
                            stringValue(result.get("target"), "(unknown target)"), caseCount, checklist);
                })
                .toList();
    }

    private static TestScopeReviewResult.ManagedTestCase normalizeManagedCase(
            TestScopeReviewResult.ManagedTestCase testCase) {
        return new TestScopeReviewResult.ManagedTestCase(
                stringValue(testCase.id(), "TC"),
                stringValue(testCase.type(), "manual").toLowerCase(),
                stringValue(testCase.input(), ""),
                stringValue(testCase.expected(), ""),
                stringValue(testCase.rationale(), ""),
                stringValue(testCase.evidence(), ""),
                normalizeStatus(testCase.status()),
                normalizePriority(testCase.priority()));
    }

    private static String normalizeStatus(String status) {
        String normalized = status == null ? "" : status.trim().toLowerCase();
        return switch (normalized) {
            case "accepted", "rejected", "backlog" -> normalized;
            default -> "accepted";
        };
    }

    private static String normalizePriority(String priority) {
        String normalized = priority == null ? "" : priority.trim().toLowerCase();
        return switch (normalized) {
            case "high", "medium", "low" -> normalized;
            default -> "medium";
        };
    }

    private static int countStatus(List<TestScopeReviewResult.ManagedTestCase> cases, String status) {
        return (int) cases.stream().filter(testCase -> status.equals(testCase.status())).count();
    }

    private static int numberValue(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static List<Evidence> combinedEvidence(
            Artifact<Object> requirement, Artifact<Object> impact, List<Artifact<Object>> tests) {
        LinkedHashSet<Evidence> evidence = new LinkedHashSet<>();
        evidence.addAll(requirement.evidence());
        evidence.addAll(impact.evidence());
        tests.forEach(test -> evidence.addAll(test.evidence()));
        return new ArrayList<>(evidence);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> mapList(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof List<?> list)) {
            return Collections.emptyList();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    private static String stringValue(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? fallback : text;
    }

    /**
     * Non-blocking hint that a successful skill run is eligible for the
     * SkillAsset vetting pipeline (skills/SkillAssetService). Deliberately
     * does not call submitForReview itself — submission stays an explicit,
     * human-triggered action via POST /api/skills/{skillName}/submit-for-review,
     * so routine skill usage doesn't spam the approvals table with an entry
     * per invocation.
     */
    private void logSkillReviewOpportunity(String skillName, String taskId) {
        REVIEW_OPPORTUNITY_LOG.info(
                "Skill '{}' completed successfully (artifact {}). Eligible for review: "
                        + "POST /api/skills/{}/submit-for-review",
                skillName, taskId, skillName);
    }

    private void requireSkillAllowed(String profile, String skill) {
        if (!agentRegistry.require(profile).allowedSkills().contains(skill)) {
            throw new IllegalArgumentException("Profile '" + profile + "' cannot use skill '" + skill + "'");
        }
    }
}
