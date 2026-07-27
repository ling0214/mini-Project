package com.miniproject.backend.agent;

import java.util.Set;

/**
 * A bounded role agent (agents/*.md): which profile it represents and which
 * skills it is allowed to invoke. Deliberately just a permission boundary,
 * not a planner — CoordinatorService still decides which skill method to
 * call based on which REST endpoint was hit, an agent only says whether its
 * profile is allowed to. Autonomous planning (an agent deciding *which*
 * skill to call from free text) is future work — see docs/proposal.md
 * Section 5.7 for why that needs an LLM this codebase doesn't wire up yet.
 */
public interface Agent {

    String profile();

    Set<String> allowedSkills();
}
