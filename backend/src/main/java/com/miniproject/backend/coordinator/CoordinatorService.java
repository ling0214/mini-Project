package com.miniproject.backend.coordinator;

import com.miniproject.backend.artifact.Artifact;
import com.miniproject.backend.skills.CodeQaResult;
import com.miniproject.backend.skills.CodeQaSkill;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * agents/coordinator-agent.md: deterministic routing only. The active
 * profile is asserted by the caller (the UI's profile switcher), not
 * guessed by an LLM — see the "why this doesn't need an LLM" note there.
 */
@Service
public class CoordinatorService {

    private static final Map<String, Set<String>> PROFILE_SKILLS = Map.of(
            "project-analyst", Set.of("code-qa", "impact-analysis", "weekly-report"),
            "tester", Set.of("code-qa", "test-case-gen"));

    private final CodeQaSkill codeQaSkill;

    public CoordinatorService(CodeQaSkill codeQaSkill) {
        this.codeQaSkill = codeQaSkill;
    }

    public Artifact<CodeQaResult> codeQa(String profile, String question) {
        requireSkillAllowed(profile, "code-qa");
        CodeQaResult result = codeQaSkill.run(question);
        String agent = "tester".equals(profile) ? "tester-agent" : "project-analyst-agent";
        return Artifact.draft(agent, "code-qa", result, result.evidence());
    }

    private void requireSkillAllowed(String profile, String skill) {
        Set<String> allowed = PROFILE_SKILLS.get(profile);
        if (allowed == null) {
            throw new IllegalArgumentException("Unknown profile: " + profile);
        }
        if (!allowed.contains(skill)) {
            throw new IllegalArgumentException("Profile '" + profile + "' cannot use skill '" + skill + "'");
        }
    }
}
