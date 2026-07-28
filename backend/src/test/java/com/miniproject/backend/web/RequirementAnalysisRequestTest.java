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
                "Given a customer is paying, when OTP is valid, then payment can continue.",
                "Stakeholder asked to keep existing card validation.");

        assertThat(request.analysisInput())
                .contains("Ticket key: PAY-102")
                .contains("Title: Add OTP verification during payment")
                .contains("Priority: High")
                .contains("Reporter: Product owner")
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
                null);

        assertThat(request.analysisInput()).isEqualTo("Customer must be able to update payment method.");
    }
}
