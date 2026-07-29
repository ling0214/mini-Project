package com.miniproject.backend.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ClarifyRequirementAnalysisRequestTest {

    @Test
    void formatsStructuredClarificationAnswers() {
        ClarifyRequirementAnalysisRequest request = new ClarifyRequirementAnalysisRequest(
                "software-analyst",
                "Confirmed by product owner during ticket review.",
                List.of(new ClarifyRequirementAnalysisRequest.ClarificationAnswer(
                        "missing_information",
                        "",
                        "Should filters persist after page reload?",
                        "Filters can reset after page reload for this phase.",
                        "missing information")));

        assertThat(request.hasClarification()).isTrue();
        assertThat(request.clarificationText())
                .contains("Question: Should filters persist after page reload?")
                .contains("Answer: Filters can reset after page reload for this phase.")
                .contains("Additional note")
                .contains("Confirmed by product owner");
    }

    @Test
    void ignoresBlankStructuredAnswers() {
        ClarifyRequirementAnalysisRequest request = new ClarifyRequirementAnalysisRequest(
                "software-analyst",
                "",
                List.of(new ClarifyRequirementAnalysisRequest.ClarificationAnswer(
                        "analyst_concern",
                        "privacy",
                        "Should donor users see exact city-level request data?",
                        " ",
                        "privacy concern")));

        assertThat(request.hasClarification()).isFalse();
        assertThat(request.clarificationText()).isBlank();
    }
}
