package com.miniproject.backend.web;

import com.miniproject.backend.skills.RequirementAnalysisSkill;
import com.miniproject.backend.skills.RequirementAnalysisResult;
import com.miniproject.backend.skills.RuleBasedRequirementAnalysisSynthesizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AnalysisStatusTest {

    private final RequirementAnalysisSkill skill = new RequirementAnalysisSkill(new RuleBasedRequirementAnalysisSynthesizer());

    @Test
    void vagueDescriptionWithMissingInformationNeedsClarification() {
        RequirementAnalysisResult result = skill.run("Maybe fix the payment thing later.");

        assertThat(AnalysisStatus.from(result)).isEqualTo(AnalysisStatus.NEEDS_CLARIFICATION);
    }

    @Test
    void concreteDescriptionWithNoMissingInformationIsReadyForReview() {
        RequirementAnalysisResult result = skill.run(
                "The customer must be able to change the payment_method after checkout is submitted.");

        assertThat(AnalysisStatus.from(result)).isEqualTo(AnalysisStatus.READY_FOR_REVIEW);
    }
}
