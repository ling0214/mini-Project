package com.miniproject.backend.agent;

import org.springframework.stereotype.Component;

import java.util.Set;

/** profiles/business-analyst.md. */
@Component
public class BusinessAnalystAgent implements Agent {

    @Override
    public String profile() {
        return "business-analyst";
    }

    @Override
    public Set<String> allowedSkills() {
        return Set.of("code-qa", "impact-analysis");
    }
}
