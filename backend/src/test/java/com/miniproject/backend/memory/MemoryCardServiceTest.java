package com.miniproject.backend.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryCardServiceTest {

    private final MemoryCardRepository repository = mock(MemoryCardRepository.class);
    private final ObjectMapper objectMapper =
            new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private final MemoryCardService service = new MemoryCardService(repository, objectMapper);

    @Test
    void recordReviewedSkipsUnsupportedSkills() throws Exception {
        service.recordReviewed("task-1", "test-case-gen", "{}");

        verify(repository, never()).save(any());
    }

    @Test
    void recordReviewedBuildsSummaryAndSearchTermsForRequirementAnalysis() throws Exception {
        String resultJson = objectMapper.writeValueAsString(Map.of(
                "business_value", "Lets donors filter aid requests by city and urgency.",
                "business_rules", List.of("Only approved aid requests are visible to donors."),
                "potential_affected_areas", List.of("AidRequestController", "donor dashboard")));

        service.recordReviewed("task-1", "requirement-analysis", resultJson);

        var captor = org.mockito.ArgumentCaptor.forClass(MemoryCardEntity.class);
        verify(repository).save(captor.capture());
        MemoryCardEntity saved = captor.getValue();
        assertThat(saved.getTaskId()).isEqualTo("task-1");
        assertThat(saved.getSkill()).isEqualTo("requirement-analysis");
        assertThat(saved.getSummaryMarkdown()).contains("donors").contains("aid requests");
        assertThat(saved.getSearchTerms()).contains("donors").contains("aid").contains("controller");
    }

    @Test
    void recordReviewedBuildsSummaryForImpactAnalysis() throws Exception {
        String resultJson = objectMapper.writeValueAsString(Map.of(
                "risk_level", "high",
                "affected_modules", List.of(Map.of("name", "AuthController", "reason", "login flow changes"))));

        service.recordReviewed("task-2", "impact-analysis", resultJson);

        var captor = org.mockito.ArgumentCaptor.forClass(MemoryCardEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getSummaryMarkdown()).contains("Risk: high").contains("AuthController");
    }

    @Test
    void findSimilarScoresByTermOverlapAndExcludesGivenTaskId() {
        MemoryCardEntity matching = new MemoryCardEntity(
                "past-1", "requirement-analysis", "Donor filtering change", "donor filtering aidrequest city urgency", Instant.now());
        MemoryCardEntity selfCard = new MemoryCardEntity(
                "current-task", "requirement-analysis", "Same ticket", "donor filtering aidrequest city urgency", Instant.now());
        MemoryCardEntity unrelated = new MemoryCardEntity(
                "past-2", "requirement-analysis", "Unrelated notification change", "email notification template", Instant.now());
        when(repository.findBySkill("requirement-analysis")).thenReturn(List.of(matching, selfCard, unrelated));

        List<SimilarPastChange> similar = service.findSimilar(
                "requirement-analysis", "Donor should filter aid requests by city and urgency.", "current-task");

        assertThat(similar).hasSize(1);
        assertThat(similar.get(0).taskId()).isEqualTo("past-1");
    }

    @Test
    void findSimilarReturnsEmptyWhenNoTermsExtracted() {
        List<SimilarPastChange> similar = service.findSimilar("requirement-analysis", "", null);

        assertThat(similar).isEmpty();
    }
}
