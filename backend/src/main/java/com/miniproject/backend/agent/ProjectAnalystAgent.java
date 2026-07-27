package com.miniproject.backend.agent;

import org.springframework.stereotype.Component;

import java.util.Set;

/** agents/project-analyst-agent.md. */
@Component
public class ProjectAnalystAgent implements Agent {

    @Override
    public String profile() {
        return "project-analyst";
    }

    @Override
    public Set<String> allowedSkills() {
        return Set.of("code-qa", "impact-analysis", "timeline-estimation", "weekly-report");
    }
}
