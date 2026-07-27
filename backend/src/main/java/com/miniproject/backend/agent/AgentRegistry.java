package com.miniproject.backend.agent;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * agents/coordinator-agent.md: looks up the bounded Agent for a profile.
 * Spring collects every Agent bean automatically — adding a new role is one
 * new @Component implementing Agent, no change here (same reusability claim
 * as CoordinatorService.PROFILE_SKILLS used to make as a static map, now
 * backed by real classes instead of map literals).
 */
@Component
public class AgentRegistry {

    private final Map<String, Agent> agentsByProfile;

    public AgentRegistry(List<Agent> agents) {
        this.agentsByProfile = agents.stream().collect(Collectors.toMap(Agent::profile, Function.identity()));
    }

    public Agent require(String profile) {
        Agent agent = agentsByProfile.get(profile);
        if (agent == null) {
            throw new IllegalArgumentException("Unknown profile: " + profile);
        }
        return agent;
    }
}
