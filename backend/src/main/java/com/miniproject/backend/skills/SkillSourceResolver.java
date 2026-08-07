package com.miniproject.backend.skills;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * Maps a kebab-case skill identifier (the same strings used in
 * Agent#allowedSkills() and CoordinatorService#requireSkillAllowed) to the
 * Java source file backing it, so SkillScannerService has real code to scan
 * instead of an empty placeholder. Not every skill identifier has a
 * dedicated *Skill.java file — some (test-scope-review, timeline-estimation,
 * handoff-summary) are implemented as logic inline in CoordinatorService —
 * those resolve to empty, which callers must treat as "cannot verify", not
 * as a clean scan.
 */
@Component
public class SkillSourceResolver {

    private static final Map<String, String> SKILL_NAME_TO_CLASS = Map.of(
            "code-qa", "CodeQaSkill",
            "impact-analysis", "ImpactAnalysisSkill",
            "requirement-analysis", "RequirementAnalysisSkill",
            "test-case-gen", "TestCaseGenSkill",
            "hermes-setup-wizard", "HermesSetupWizardSkill",
            "hermes-version-advisor", "HermesVersionAdvisorSkill",
            "hermes-trending-digest", "HermesTrendingDigestSkill");

    private static final String SOURCE_SUBPATH =
            "src/main/java/com/miniproject/backend/skills";

    /**
     * Returns the skill's source code, or empty if there is no dedicated
     * source file for this skill identifier (unmapped, or the mapped file
     * is missing on disk). Only works when running from a source checkout —
     * mvn's working directory is either the repo root or backend/ depending
     * on how it's invoked, so both are tried; a packaged jar has no source
     * tree to read at all, and resolves to empty like any other miss.
     */
    public Optional<String> resolveSource(String skillName) {
        String className = SKILL_NAME_TO_CLASS.get(skillName);
        if (className == null) {
            return Optional.empty();
        }

        String userDir = System.getProperty("user.dir");
        Path fromBackendCwd = Path.of(userDir, SOURCE_SUBPATH, className + ".java");
        Path fromRepoRootCwd = Path.of(userDir, "backend", SOURCE_SUBPATH, className + ".java");
        Path file = Files.isRegularFile(fromBackendCwd) ? fromBackendCwd : fromRepoRootCwd;

        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }

        try {
            return Optional.of(Files.readString(file));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read skill source for " + skillName, e);
        }
    }
}
