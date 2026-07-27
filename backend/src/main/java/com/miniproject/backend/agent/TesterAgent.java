package com.miniproject.backend.agent;

import org.springframework.stereotype.Component;

import java.util.Set;

/** Legacy compatibility profile. The guided workflow uses SoftwareAnalystAgent. */
@Component
public class TesterAgent implements Agent {

    @Override
    public String profile() {
        return "tester";
    }

    @Override
    public Set<String> allowedSkills() {
        return Set.of("code-qa", "test-case-gen");
    }
}
