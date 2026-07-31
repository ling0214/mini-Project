package com.miniproject.backend.skills;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmAnswerSynthesizerTest {

    private final AiAnalysisClient aiAnalysisClient = mock(AiAnalysisClient.class);
    private final RuleBasedAnswerSynthesizer fallback = new RuleBasedAnswerSynthesizer();
    private final LlmAnswerSynthesizer synthesizer = new LlmAnswerSynthesizer(aiAnalysisClient, fallback);

    @Test
    void usesAiAnswerWithEvidenceFromResolvedEndpoints() {
        when(aiAnalysisClient.analyze(any(), any()))
                .thenReturn(Optional.of("Check the DonationController before changing status."));
        List<Map<String, Object>> endpoints = List.of(Map.of(
                "name", "updateStatus", "file", "app/Http/Controllers/DonationController.php", "line", 42,
                "calls", List.of(), "called_by", List.of()));

        CodeQaResult result = synthesizer.synthesize("What should I check?", endpoints, Map.of());

        assertThat(result.answer()).isEqualTo("Check the DonationController before changing status.");
        assertThat(result.evidence()).hasSize(1);
        assertThat(result.evidence().get(0).source()).isEqualTo("app/Http/Controllers/DonationController.php:42");
        assertThat(result.ungrounded()).isEmpty();
    }

    @Test
    void flagsUngroundedWhenNoEndpointsResolvedEvenThoughAiAnswered() {
        when(aiAnalysisClient.analyze(any(), any())).thenReturn(Optional.of("General guidance based on reasoning alone."));

        CodeQaResult result = synthesizer.synthesize("What should I check?", List.of(), Map.of());

        assertThat(result.answer()).isEqualTo("General guidance based on reasoning alone.");
        assertThat(result.ungrounded()).isNotEmpty();
    }

    @Test
    void fallsBackToRuleBasedWhenAiReturnsEmpty() {
        when(aiAnalysisClient.analyze(any(), any())).thenReturn(Optional.empty());

        CodeQaResult result = synthesizer.synthesize("What should I check?", List.of(), Map.of());

        assertThat(result.answer()).isEqualTo("Nothing in the project graph or issue tracker matched this question.");
    }

    @Test
    void fallsBackWhenAiReturnsBlankString() {
        when(aiAnalysisClient.analyze(any(), any())).thenReturn(Optional.of("   "));

        CodeQaResult result = synthesizer.synthesize("What should I check?", List.of(), Map.of());

        assertThat(result.answer()).isEqualTo("Nothing in the project graph or issue tracker matched this question.");
    }

    @Test
    void passesQuestionAndFactsIntoThePrompt() {
        when(aiAnalysisClient.analyze(any(), any())).thenReturn(Optional.of("answer"));
        List<Map<String, Object>> endpoints = List.of(Map.of(
                "name", "charge_card", "file", "payments.py", "line", 10, "calls", List.of(), "called_by", List.of()));
        Map<String, Object> issueSearch = Map.of("matches", List.of(Map.of("id", 108, "state", "open", "title", "Gateway timeout")));

        synthesizer.synthesize("What calls charge_card?", endpoints, issueSearch);

        verify(aiAnalysisClient).analyze(any(), org.mockito.ArgumentMatchers.contains("charge_card"));
    }
}
