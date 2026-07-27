package com.miniproject.backend.skills;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequirementAnalysisSkillTest {

    private final RequirementAnalysisSkill skill = new RequirementAnalysisSkill(new RuleBasedRequirementAnalysisSynthesizer());

    @Test
    void shortVagueDescriptionIsFlaggedAsLowConfidenceWithMissingInformation() {
        RequirementAnalysisResult result = skill.run("Maybe fix the payment thing later.");

        assertThat(result.confidence()).isEqualTo("low");
        assertThat(result.missingInformation()).isNotEmpty();
        assertThat(result.ambiguities()).isNotEmpty();
    }

    @Test
    void concreteDescriptionWithActorAndModuleExtractsBusinessRuleAndCandidateArea() {
        RequirementAnalysisResult result = skill.run(
                "The customer must be able to change the payment_method after checkout is submitted.");

        assertThat(result.businessRules()).anyMatch(rule -> rule.contains("must be able to change"));
        assertThat(result.potentialAffectedAreas()).contains("payment_method");
        assertThat(result.missingInformation()).isEmpty();
    }

    @Test
    void descriptionWithNoActorFlagsMissingActor() {
        RequirementAnalysisResult result = skill.run(
                "The payment_method field should update automatically whenever the gateway_timeout event fires.");

        assertThat(result.missingInformation()).anyMatch(m -> m.contains("No actor/role"));
    }
}
