package com.miniproject.backend.agent;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SoftwareAnalystAgentTest {

    @Test
    void allowsTheFullSoftwareAnalystWorkflow() {
        SoftwareAnalystAgent agent = new SoftwareAnalystAgent();

        assertThat(agent.profile()).isEqualTo("software-analyst");
        assertThat(agent.allowedSkills()).contains(
                "requirement-analysis",
                "impact-analysis",
                "test-case-gen",
                "test-scope-review",
                "code-qa",
                "timeline-estimation",
                "handoff-summary");
    }
}
