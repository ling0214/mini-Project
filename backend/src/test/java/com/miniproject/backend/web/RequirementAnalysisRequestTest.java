package com.miniproject.backend.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RequirementAnalysisRequestTest {

    @Test
    void combinesTicketFieldsIntoAnalysisInput() {
        RequirementAnalysisRequest request = new RequirementAnalysisRequest(
                "software-analyst",
                "Customer must confirm OTP before payment is submitted.",
                "PAY-102",
                "Add OTP verification during payment",
                "High",
                "Product owner",
                "Jira",
                "Jira import",
                "https://jira.example.local/browse/PAY-102",
                "Today 09:30",
                "Given a customer is paying, when OTP is valid, then payment can continue.",
                "Stakeholder asked to keep existing card validation.",
                null);

        assertThat(request.analysisInput())
                .contains("Ticket key: PAY-102")
                .contains("Title: Add OTP verification during payment")
                .contains("Priority: High")
                .contains("Reporter: Product owner")
                .contains("Source type: Jira")
                .contains("Source name: Jira import")
                .contains("Source URL: https://jira.example.local/browse/PAY-102")
                .contains("Received: Today 09:30")
                .contains("Description:\nCustomer must confirm OTP before payment is submitted.")
                .contains("Acceptance criteria:\nGiven a customer is paying")
                .contains("Comments / notes:\nStakeholder asked");
    }

    @Test
    void keepsLegacyDescriptionOnlyRequestWorking() {
        RequirementAnalysisRequest request = new RequirementAnalysisRequest(
                "software-analyst",
                "Customer must be able to update payment method.",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);

        assertThat(request.analysisInput()).isEqualTo("Customer must be able to update payment method.");
    }

    @Test
    void includesManuallyPastedCodeSnippetLabeledDistinctlyFromAutomatedScan() {
        RequirementAnalysisRequest request = new RequirementAnalysisRequest(
                "software-analyst",
                "Donation status should update when payment confirms.",
                null, null, null, null, null, null, null, null, null, null,
                "public function markPaid(Donation $donation) { $donation->status = 'paid'; }");

        assertThat(request.analysisInput())
                .contains("Code evidence:")
                .contains("public function markPaid(Donation $donation)");
    }

    @Test
    void codeSnippetAloneWithNoOtherFieldsStillProducesStructuredInput() {
        RequirementAnalysisRequest request = new RequirementAnalysisRequest(
                "software-analyst", null, null, null, null, null, null, null, null, null, null, null,
                "class Donation { public $status; }");

        assertThat(request.analysisInput()).contains("Code evidence:\nclass Donation");
    }
}
