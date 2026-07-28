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

    @Test
    void myBanjirCareDonorTicketIsRecognizedAsHavingActor() {
        RequirementAnalysisResult result = skill.run(
                "Donor should be able to filter approved aid request records by city, category, and urgency before responding to help.");

        assertThat(result.missingInformation()).noneMatch(m -> m.contains("No actor/role"));
    }

    @Test
    void myBanjirCareDonorTicketFlagsAnalystConcerns() {
        RequirementAnalysisResult result = skill.run(
                "Donor should be able to filter approved aid request records by city, category, and urgency before responding to help.");

        assertThat(result.analystConcerns())
                .extracting("category")
                .contains("privacy", "role_access", "performance", "testing");
        assertThat(result.analystConcerns())
                .allMatch(concern -> !concern.question().isBlank());
    }

    @Test
    void ticketMetadataLabelsAreNotCandidateAreas() {
        RequirementAnalysisResult result = skill.run("""
                Ticket key: MBC-204
                Title: Allow donors to filter available aid requests by city and urgency
                Priority: High
                Reporter: FYP Supervisor

                Description:
                Donor should be able to filter approved aid request records by city, category, and urgency.
                """);

        assertThat(result.potentialAffectedAreas())
                .doesNotContain("Ticket", "key", "Title", "Priority", "Reporter", "MBC", "FYP", "Supervisor", "High")
                .contains("donors", "aid", "requests", "city", "urgency");
    }

    @Test
    void ticketMetadataDoesNotLeakIntoBusinessRuleText() {
        RequirementAnalysisResult result = skill.run("""
                Ticket key: MBC-204
                Title: Allow donors to filter available aid requests by city and urgency
                Priority: High
                Reporter: FYP Supervisor

                Description:
                Donor should be able to filter approved aid request records by city, category, and urgency before responding to help.
                """);

        assertThat(result.businessRules()).containsExactly(
                "Donor should be able to filter approved aid request records by city, category, and urgency before responding to help.");
        assertThat(result.businessRules().get(0)).doesNotContain("Ticket key", "Reporter");
    }
}
