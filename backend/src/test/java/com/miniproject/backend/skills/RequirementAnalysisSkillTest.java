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
    void analystConcernEvidenceIsTargetedNotTheWholeTicketDumpRepeated() {
        String input = """
                Ticket key: MBC-204
                Title: Allow donors to filter available aid requests by city and urgency
                Priority: High
                Reporter: Supervisor

                Description:
                Donor should be able to filter approved aid request records by city, category, and urgency before responding to help.
                Only admin users can approve requests.
                Filtering must stay fast even as record volume grows.
                """;
        RequirementAnalysisResult result = skill.run(input);

        assertThat(result.analystConcerns()).hasSizeGreaterThanOrEqualTo(2);
        // Each concern's evidence must be a targeted excerpt, meaningfully
        // shorter than the whole input, and distinct from every other
        // concern's evidence -- previously every concern carried the
        // identical, full raw ticket dump verbatim.
        java.util.Set<String> distinctEvidence = new java.util.HashSet<>();
        for (RequirementAnalysisResult.AnalystConcern concern : result.analystConcerns()) {
            distinctEvidence.add(concern.evidence());
            assertThat(concern.evidence().length())
                    .as("evidence for " + concern.category() + " should be shorter than the whole ticket dump")
                    .isLessThan(input.trim().length());
        }
        assertThat(distinctEvidence).hasSizeGreaterThan(1);
    }

    @Test
    void ticketMetadataLabelsAreNotCandidateAreas() {
        RequirementAnalysisResult result = skill.run("""
                Ticket key: MBC-204
                Title: Allow donors to filter available aid requests by city and urgency
                Priority: High
                Reporter:  Supervisor

                Description:
                Donor should be able to filter approved aid request records by city, category, and urgency.
                """);

        assertThat(result.potentialAffectedAreas())
                .doesNotContain("Ticket", "key", "Title", "Priority", "Reporter", "MBC", "FYP", "Supervisor", "High")
                .contains("donors", "aid", "requests", "city", "urgency");
    }

    @Test
    void urlsDoNotLeakGarbageTokensIntoCandidateAreas() {
        RequirementAnalysisResult result = skill.run("""
                Ticket key: KAN-4
                Title: Allow donors to filter approved aid requests by city and urgency
                Source URL: https://ng-ling-ling.atlassian.net/browse/KAN-4

                Description:
                Donor should be able to filter approved aid request records by city, category, and urgency.
                Code evidence URL: https://github.com/org/repo/blob/main/app/Http/Controllers/DonationController.php
                """);

        assertThat(result.potentialAffectedAreas())
                .doesNotContain("https", "github", "com", "org", "repo", "blob", "atlassian", "net", "browse", "php")
                .contains("donors", "aid", "requests", "city", "urgency");
    }

    @Test
    void ticketMetadataDoesNotLeakIntoBusinessRuleText() {
        RequirementAnalysisResult result = skill.run("""
                Ticket key: MBC-204
                Title: Allow donors to filter available aid requests by city and urgency
                Priority: High
                Reporter:  Supervisor

                Description:
                Donor should be able to filter approved aid request records by city, category, and urgency before responding to help.
                """);

        assertThat(result.businessRules()).containsExactly(
                "Donor should be able to filter approved aid request records by city, category, and urgency before responding to help.");
        assertThat(result.businessRules().get(0)).doesNotContain("Ticket key", "Reporter");
    }
}
