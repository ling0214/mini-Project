package com.miniproject.backend.agent;

import org.springframework.stereotype.Component;

import java.util.Set;

/** Unified workflow profile for the Software Analyst assistant. */
@Component
public class SoftwareAnalystAgent implements Agent {

    @Override
    public String profile() {
        return "software-analyst";
    }

    @Override
    public Set<String> allowedSkills() {
        return Set.of(
                "code-qa",
                "requirement-analysis",
                "impact-analysis",
                "test-case-gen",
                "test-scope-review",
                "timeline-estimation",
                "handoff-summary");
    }
}
