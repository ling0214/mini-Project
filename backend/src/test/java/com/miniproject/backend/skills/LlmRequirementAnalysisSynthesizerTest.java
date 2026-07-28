package com.miniproject.backend.skills;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LlmRequirementAnalysisSynthesizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RuleBasedRequirementAnalysisSynthesizer fallback = new RuleBasedRequirementAnalysisSynthesizer();

    @Test
    void parsesAiJsonIntoRequirementAnalysisResult() {
        AiAnalysisClient aiClient = (systemPrompt, userPrompt) -> Optional.of("""
                {
                  "business_rules": ["Donors can filter approved aid requests by city and urgency."],
                  "ambiguities": [{"note": "Category behavior is unclear", "evidence": "filter by city and urgency"}],
                  "missing_information": ["Should filters persist after page reload?"],
                  "assumptions": ["Filtering applies only to approved requests."],
                  "analyst_concerns": [
                    {
                      "category": "privacy",
                      "severity": "medium",
                      "note": "City and aid request records may expose location-related victim data.",
                      "evidence": "filter approved aid request records by city",
                      "question": "Should donor users see exact city-level request data?"
                    }
                  ],
                  "potential_affected_areas": ["Aid Request Listing", "Donation Flow"],
                  "confidence": "high"
                }
                """);

        LlmRequirementAnalysisSynthesizer synthesizer =
                new LlmRequirementAnalysisSynthesizer(aiClient, fallback, objectMapper);

        RequirementAnalysisResult result = synthesizer.synthesize(
                "Donor should be able to filter approved aid request records by city and urgency.",
                List.of("Donor should be able to filter approved aid request records by city and urgency."),
                List.of("donor", "aid", "requests", "city", "urgency"));

        assertThat(result.confidence()).isEqualTo("high");
        assertThat(result.businessRules()).containsExactly("Donors can filter approved aid requests by city and urgency.");
        assertThat(result.missingInformation()).containsExactly("Should filters persist after page reload?");
        assertThat(result.potentialAffectedAreas()).containsExactly("Aid Request Listing", "Donation Flow");
        assertThat(result.analystConcerns())
                .extracting("category")
                .containsExactly("privacy");
        assertThat(result.analystConcerns())
                .extracting("severity")
                .containsExactly("medium");
        assertThat(result.evidence()).extracting("source").containsExactly("AI requirement analysis");
    }

    @Test
    void fallsBackToRuleBasedAnalysisWhenAiReturnsNothing() {
        AiAnalysisClient aiClient = (systemPrompt, userPrompt) -> Optional.empty();
        LlmRequirementAnalysisSynthesizer synthesizer =
                new LlmRequirementAnalysisSynthesizer(aiClient, fallback, objectMapper);

        RequirementAnalysisResult result = synthesizer.synthesize(
                "The customer must be able to change the payment_method after checkout is submitted.",
                List.of("The customer must be able to change the payment_method after checkout is submitted."),
                List.of("payment_method"));

        assertThat(result.businessRules()).anyMatch(rule -> rule.contains("must be able to change"));
        assertThat(result.potentialAffectedAreas()).contains("payment_method");
        assertThat(result.evidence()).extracting("source").contains("requirement text");
    }
}
