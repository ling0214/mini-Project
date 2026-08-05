package com.miniproject.backend.skills;

import com.miniproject.backend.integrations.GitLogReader;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Boss idea #1 ("AI-assisted advisor for which upstream commit/tag to adopt,
 * not a blind git pull, with memory") — see the mini-Project <-> Hermes
 * implementation plan. Memory is free: reviewing this skill's Artifact
 * already runs it through MemoryCardService.recordReviewed() like every
 * other skill.
 */
@Component
public class HermesVersionAdvisorSkill {

    private final GitLogReader gitLogReader;
    private final HermesVersionAdvisorSynthesizer synthesizer;

    public HermesVersionAdvisorSkill(GitLogReader gitLogReader, HermesVersionAdvisorSynthesizer synthesizer) {
        this.gitLogReader = gitLogReader;
        this.synthesizer = synthesizer;
    }

    public HermesVersionAdvisorResult run(String repoPath, String remoteRef, String localRef, List<String> watchedPaths) {
        if (repoPath == null || repoPath.isBlank()) {
            throw new IllegalArgumentException("repoPath is required");
        }
        String cleanedRemoteRef = remoteRef == null || remoteRef.isBlank() ? "origin/main" : remoteRef.trim();
        String cleanedLocalRef = localRef == null || localRef.isBlank() ? "main" : localRef.trim();
        GitLogReader.GitFacts facts =
                gitLogReader.readFacts(repoPath.trim(), cleanedRemoteRef, cleanedLocalRef, watchedPaths);
        return synthesizer.synthesize(repoPath.trim(), facts);
    }
}
