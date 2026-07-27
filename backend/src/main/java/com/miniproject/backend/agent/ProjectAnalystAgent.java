package com.miniproject.backend.agent;

import org.springframework.stereotype.Component;

import java.util.Set;

/** Legacy compatibility profile. The guided workflow uses SoftwareAnalystAgent. */
@Component
public class ProjectAnalystAgent implements Agent {

    @Override
    public String profile() {
        return "project-analyst";
    }

    @Override
    public Set<String> allowedSkills() {
        return Set.of("code-qa", "impact-analysis", "timeline-estimation");
    }
}
