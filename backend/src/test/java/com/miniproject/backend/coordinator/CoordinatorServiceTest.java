package com.miniproject.backend.coordinator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.miniproject.backend.agent.AgentRegistry;
import com.miniproject.backend.agent.SoftwareAnalystAgent;
import com.miniproject.backend.artifact.Artifact;
import com.miniproject.backend.artifact.Evidence;
import com.miniproject.backend.github.GitHubPrReader;
import com.miniproject.backend.github.ScopeCreepDetector;
import com.miniproject.backend.memory.MemoryCardService;
import com.miniproject.backend.persistence.ArtifactPersistenceService;
import com.miniproject.backend.skills.CodeQaSkill;
import com.miniproject.backend.skills.HandoffSummaryResult;
import com.miniproject.backend.skills.HermesSetupWizardSkill;
import com.miniproject.backend.skills.HermesTrendingDigestSkill;
import com.miniproject.backend.skills.HermesVersionAdvisorSkill;
import com.miniproject.backend.skills.ImpactAnalysisResult;
import com.miniproject.backend.skills.ImpactAnalysisSkill;
import com.miniproject.backend.skills.RequirementAnalysisResult;
import com.miniproject.backend.skills.RequirementAnalysisSkill;
import com.miniproject.backend.skills.TestCaseGenSkill;
import com.miniproject.backend.skills.TestScopeReviewResult;
import com.miniproject.backend.skills.TimelineEstimationSynthesizer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoordinatorServiceTest {

    private final ArtifactPersistenceService persistence = mock(ArtifactPersistenceService.class);
    private final ObjectMapper objectMapper =
            new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private final RequirementAnalysisSkill requirementAnalysisSkill = mock(RequirementAnalysisSkill.class);
    private final MemoryCardService memoryCardService = mock(MemoryCardService.class);
    private final ImpactAnalysisSkill impactAnalysisSkill = mock(ImpactAnalysisSkill.class);
    private final GitHubPrReader prReader = mock(GitHubPrReader.class);
    private final ScopeCreepDetector scopeCreepDetector = mock(ScopeCreepDetector.class);
    private final CoordinatorService coordinator = new CoordinatorService(
            mock(CodeQaSkill.class),
            impactAnalysisSkill,
            prReader,
            scopeCreepDetector,
            mock(TestCaseGenSkill.class),
            mock(TimelineEstimationSynthesizer.class),
            requirementAnalysisSkill,
            mock(HermesSetupWizardSkill.class),
            mock(HermesVersionAdvisorSkill.class),
            mock(HermesTrendingDigestSkill.class),
            persistence,
            new AgentRegistry(List.of(new SoftwareAnalystAgent())),
            objectMapper,
            memoryCardService);

    @Test
    void clarificationHistorySingleRoundHasNoAnsweredText() throws Exception {
        RequirementAnalysisResult result = requirementResult(List.of("Missing OTP expiry rule"));
        String resultJson = objectMapper.writeValueAsString(result);
        Artifact<Object> root = artifact("req-1", "requirement-analysis", false, Map.of());
        when(persistence.findArtifact("req-1")).thenReturn(Optional.of(root));
        when(persistence.findClarificationChain("req-1")).thenReturn(List.of(
                new ArtifactPersistenceService.ChainEntry(
                        "req-1", "requirement-analysis", Instant.now(), false,
                        "Add OTP verification during login.", resultJson)));

        List<ClarificationHistoryEntry> history = coordinator.clarificationHistory("req-1");

        assertThat(history).hasSize(1);
        assertThat(history.get(0).clarificationAnswered()).isEmpty();
        assertThat(history.get(0).missingInformation()).containsExactly("Missing OTP expiry rule");
    }

    @Test
    void clarificationHistoryMultiRoundExtractsAnswerPerRound() throws Exception {
        String round1Json = objectMapper.writeValueAsString(requirementResult(List.of("Missing OTP expiry rule")));
        String round2Json = objectMapper.writeValueAsString(requirementResult(List.of()));
        Artifact<Object> latest = artifact("req-2", "requirement-analysis", false, Map.of());
        when(persistence.findArtifact("req-2")).thenReturn(Optional.of(latest));
        when(persistence.findClarificationChain("req-2")).thenReturn(List.of(
                new ArtifactPersistenceService.ChainEntry(
                        "req-1", "requirement-analysis", Instant.now(), false,
                        "Add OTP verification during login.", round1Json),
                new ArtifactPersistenceService.ChainEntry(
                        "req-2", "requirement-analysis", Instant.now(), false,
                        "Add OTP verification during login.\n\nClarifications:\nOTP expires after 5 minutes.",
                        round2Json)));

        List<ClarificationHistoryEntry> history = coordinator.clarificationHistory("req-2");

        assertThat(history).hasSize(2);
        assertThat(history.get(0).taskId()).isEqualTo("req-1");
        assertThat(history.get(0).clarificationAnswered()).isEmpty();
        assertThat(history.get(1).taskId()).isEqualTo("req-2");
        assertThat(history.get(1).clarificationAnswered()).isEqualTo("OTP expires after 5 minutes.");
        assertThat(history.get(1).missingInformation()).isEmpty();
    }

    @Test
    void requirementAnalysisAttachesSimilarPastChangesFromMemory() {
        RequirementAnalysisResult skillResult = requirementResult(List.of());
        when(requirementAnalysisSkill.run("Donor should filter aid requests by city.")).thenReturn(skillResult);
        com.miniproject.backend.memory.SimilarPastChange pastChange =
                new com.miniproject.backend.memory.SimilarPastChange("past-1", "Donor filtering change", 3, "2026-01-01T00:00:00Z");
        when(memoryCardService.findSimilar(eq("requirement-analysis"), eq("Donor should filter aid requests by city."), any()))
                .thenReturn(List.of(pastChange));

        Artifact<RequirementAnalysisResult> artifact =
                coordinator.requirementAnalysis("software-analyst", "Donor should filter aid requests by city.");

        assertThat(artifact.result().similarPastChanges()).containsExactly(pastChange);
    }

    @Test
    void impactAnalysisFromPrAttachesScopeCreepFindingsFromDetector() {
        GitHubPrReader.PrSummary pr = new GitHubPrReader.PrSummary(
                "acme", "widgets", 42, "Fix checkout timeout",
                List.of(
                        new GitHubPrReader.PrFile("src/CheckoutController.php", "modified", "patch text"),
                        new GitHubPrReader.PrFile("src/UnrelatedHelper.php", "modified", "patch text")));
        String prUrl = "https://github.com/acme/widgets/pull/42";
        when(prReader.fetch(prUrl)).thenReturn(pr);
        when(prReader.toChangeRequestText(pr)).thenReturn("PR #42 in acme/widgets: Fix checkout timeout.");

        ImpactAnalysisResult skillResult = new ImpactAnalysisResult(
                List.of(new ImpactAnalysisResult.AffectedModule(
                        "CheckoutController", "src/CheckoutController.php:10", "matched candidate", "src/CheckoutController.php:10")),
                List.of(), "low", new ImpactAnalysisResult.Effort("S", "1 affected module(s)"),
                List.of(), "medium", List.of(), List.of(), List.of());
        when(impactAnalysisSkill.run("PR #42 in acme/widgets: Fix checkout timeout.")).thenReturn(skillResult);

        List<ImpactAnalysisResult.ScopeCreepFinding> findings = List.of(
                new ImpactAnalysisResult.ScopeCreepFinding(
                        "src/UnrelatedHelper.php", "undeclared_change",
                        "PR modified this file, but it is not in the declared affected modules."));
        when(scopeCreepDetector.detect(pr.files(), skillResult.affectedModules())).thenReturn(findings);

        Artifact<ImpactAnalysisResult> artifact = coordinator.impactAnalysisFromPr("software-analyst", prUrl);

        assertThat(artifact.result().scopeCreepFindings()).isEqualTo(findings);
        assertThat(artifact.result().evidence()).anyMatch(evidence -> evidence.claim().equals("Source PR: Fix checkout timeout"));
        verify(persistence).save(any(Artifact.class), eq("software-analyst"), eq(prUrl));
    }

    @Test
    void clarificationHistoryRejectsNonRequirementAnalysisArtifact() {
        Artifact<Object> impact = artifact("impact-1", "impact-analysis", false, Map.of());
        when(persistence.findArtifact("impact-1")).thenReturn(Optional.of(impact));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> coordinator.clarificationHistory("impact-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static RequirementAnalysisResult requirementResult(List<String> missingInformation) {
        return new RequirementAnalysisResult(
                List.of(), "", new RequirementAnalysisResult.ScopeBoundary(List.of(), List.of(), List.of()),
                List.of(), missingInformation, List.of(), List.of(), List.of(), List.of(), "medium", List.of(), List.of());
    }

    @Test
    void reviewTestScopeCreatesLinkedManagedScopeArtifact() {
        Artifact<Object> source = artifact("test-1", "test-case-gen", false, Map.of(
                "target", "AidRequestController",
                "regression_checklist", List.of("Retest donor browsing filter")));
        when(persistence.findArtifact("test-1")).thenReturn(Optional.of(source));

        Artifact<TestScopeReviewResult> result = coordinator.reviewTestScope(
                "test-1",
                "software-analyst",
                List.of(
                        new TestScopeReviewResult.ManagedTestCase(
                                "TC-1", "positive", "Filter city=KL", "Only KL approved aid requests appear",
                                "Covers donor filter behavior", "AidRequestController.php:1", "accepted", "high"),
                        new TestScopeReviewResult.ManagedTestCase(
                                "TC-2", "edge", "Empty filter result", "Empty state is shown",
                                "Can be tested later", "donor-browse.blade.php:1", "backlog", "low")),
                "Prioritize city and urgency filters for UAT.");

        assertThat(result.skill()).isEqualTo("test-scope-review");
        assertThat(result.result().acceptedCount()).isEqualTo(1);
        assertThat(result.result().backlogCount()).isEqualTo(1);
        assertThat(result.result().readiness()).isEqualTo("READY_FOR_REVIEW");
        assertThat(result.result().cases()).extracting(TestScopeReviewResult.ManagedTestCase::priority)
                .containsExactly("high", "low");
        verify(persistence).save(any(Artifact.class), eq("software-analyst"), eq("Reviewed test scope for test-1"), eq("test-1"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void handoffSummaryPrefersReviewedManagedTestScopeOverGeneratedCases() {
        Artifact<Object> requirement = artifact("req-1", "requirement-analysis", true, Map.of(
                "business_rules", List.of("Donor can filter approved aid requests."),
                "missing_information", List.of(),
                "ambiguities", List.of(),
                "assumptions", List.of()));
        Artifact<Object> impact = artifact("impact-1", "impact-analysis", true, Map.of(
                "affected_modules", List.of(Map.of("name", "AidRequestController", "path", "AidRequestController.php:1", "reason", "filter logic")),
                "risk_notes", List.of(),
                "risk_level", "low",
                "rough_effort", Map.of("estimate", "M", "basis", "filter and view update")));
        Artifact<Object> generatedTests = artifact("test-1", "test-case-gen", false, Map.of(
                "target", "AidRequestController",
                "cases", List.of(Map.of("id", "TC-1"), Map.of("id", "TC-2"), Map.of("id", "TC-3")),
                "regression_checklist", List.of("Retest donor browsing filter")));
        Artifact<Object> reviewedScope = artifact("scope-1", "test-scope-review", true, Map.of(
                "target", "AidRequestController",
                "accepted_count", 1,
                "regression_checklist", List.of("Retest donor browsing filter")));

        when(persistence.findArtifact("req-1")).thenReturn(Optional.of(requirement));
        when(persistence.findArtifact("impact-1")).thenReturn(Optional.of(impact));
        when(persistence.findChildren("impact-1")).thenReturn(List.of(generatedTests));
        when(persistence.findChildren("test-1")).thenReturn(List.of(reviewedScope));
        when(persistence.findInputText("req-1")).thenReturn(Optional.of("Donor can filter aid requests."));

        coordinator.handoffSummary("impact-1", "software-analyst", "req-1", List.of());

        ArgumentCaptor<Artifact<HandoffSummaryResult>> captor = ArgumentCaptor.forClass(Artifact.class);
        verify(persistence).save(captor.capture(), eq("software-analyst"), any(String.class), eq("impact-1"));
        HandoffSummaryResult summary = captor.getValue().result();

        assertThat(summary.testPlans()).hasSize(1);
        assertThat(summary.testPlans().get(0).target()).isEqualTo("AidRequestController");
        assertThat(summary.testPlans().get(0).caseCount()).isEqualTo(1);
    }

    private Artifact<Object> artifact(String taskId, String skill, boolean reviewed, Map<String, Object> result) {
        return new Artifact<>(
                "artifact.v1",
                "software-analyst-agent",
                skill,
                taskId,
                null,
                Instant.now().toString(),
                result,
                List.of(new Evidence(skill + " evidence", skill + " source")),
                reviewed);
    }
}
