package com.miniproject.backend.coordinator;

import com.miniproject.backend.agent.AgentRegistry;
import com.miniproject.backend.agent.SoftwareAnalystAgent;
import com.miniproject.backend.artifact.Artifact;
import com.miniproject.backend.artifact.Evidence;
import com.miniproject.backend.github.GitHubPrReader;
import com.miniproject.backend.persistence.ArtifactPersistenceService;
import com.miniproject.backend.skills.CodeQaSkill;
import com.miniproject.backend.skills.HandoffSummaryResult;
import com.miniproject.backend.skills.ImpactAnalysisSkill;
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
    private final CoordinatorService coordinator = new CoordinatorService(
            mock(CodeQaSkill.class),
            mock(ImpactAnalysisSkill.class),
            mock(GitHubPrReader.class),
            mock(TestCaseGenSkill.class),
            mock(TimelineEstimationSynthesizer.class),
            mock(RequirementAnalysisSkill.class),
            persistence,
            new AgentRegistry(List.of(new SoftwareAnalystAgent())));

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
