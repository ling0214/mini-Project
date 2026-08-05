package com.miniproject.backend.skills;

import com.miniproject.backend.integrations.GitLogReader;

/**
 * Same seam as the other *Synthesizer interfaces: HermesVersionAdvisorSkill
 * only depends on this. Takes git facts already gathered by GitLogReader and
 * produces a reasoned recommendation (not the raw facts themselves).
 */
public interface HermesVersionAdvisorSynthesizer {

    HermesVersionAdvisorResult synthesize(String repoPath, GitLogReader.GitFacts facts);
}
