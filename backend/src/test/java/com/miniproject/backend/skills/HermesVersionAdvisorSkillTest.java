package com.miniproject.backend.skills;

import com.miniproject.backend.integrations.GitLogReader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HermesVersionAdvisorSkillTest {

    @Test
    void rejectsBlankRepoPath() {
        HermesVersionAdvisorSkill skill =
                new HermesVersionAdvisorSkill(mock(GitLogReader.class), new RuleBasedHermesVersionAdvisorSynthesizer());

        assertThatThrownBy(() -> skill.run(" ", null, null, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repoPath");
    }

    @Test
    void recommendsAdoptingLatestTagWhenNoWatchedFileCommits() {
        GitLogReader gitLogReader = mock(GitLogReader.class);
        when(gitLogReader.readFacts("C:/repos/hermes-agent", "origin/main", "main", List.of("plugins/platforms/email/adapter.py")))
                .thenReturn(new GitLogReader.GitFacts(5212, 1, "v2026.8.3", List.of("plugins/platforms/email/adapter.py"), List.of(), List.of()));

        HermesVersionAdvisorSkill skill =
                new HermesVersionAdvisorSkill(gitLogReader, new RuleBasedHermesVersionAdvisorSynthesizer());

        HermesVersionAdvisorResult result = skill.run(
                "C:/repos/hermes-agent", "origin/main", "main", List.of("plugins/platforms/email/adapter.py"));

        assertThat(result.commitsBehind()).isEqualTo(5212);
        assertThat(result.recommendedRef()).isEqualTo("v2026.8.3");
        assertThat(result.rationale()).contains("safe to adopt");
        assertThat(result.risksIfAdopted()).isEmpty();
        assertThat(result.suggestedEnhancements()).isEmpty();
    }

    @Test
    void flagsForManualReviewWhenWatchedFilesAreTouched() {
        GitLogReader gitLogReader = mock(GitLogReader.class);
        when(gitLogReader.readFacts("C:/repos/hermes-agent", "origin/main", "main", List.of("plugins/platforms/email/adapter.py")))
                .thenReturn(new GitLogReader.GitFacts(5212, 1, "v2026.8.3",
                        List.of("plugins/platforms/email/adapter.py"),
                        List.of(
                                new GitLogReader.TouchedCommit("ff89f1b86", "fix(email): scope ports/trust flag"),
                                new GitLogReader.TouchedCommit("f08f40315", "fix(email): honor profile secret scope")),
                        List.of()));

        HermesVersionAdvisorSkill skill =
                new HermesVersionAdvisorSkill(gitLogReader, new RuleBasedHermesVersionAdvisorSynthesizer());

        HermesVersionAdvisorResult result = skill.run(
                "C:/repos/hermes-agent", "origin/main", "main", List.of("plugins/platforms/email/adapter.py"));

        assertThat(result.recommendedRef()).isEqualTo("manual review required before adopting");
        assertThat(result.risksIfAdopted()).hasSize(2);
        assertThat(result.risksIfAdopted().get(0)).contains("ff89f1b86");
    }

    @Test
    void defaultsRemoteAndLocalRefsWhenBlank() {
        GitLogReader gitLogReader = mock(GitLogReader.class);
        when(gitLogReader.readFacts("C:/repos/hermes-agent", "origin/main", "main", List.of()))
                .thenReturn(new GitLogReader.GitFacts(0, 0, null, List.of(), List.of(), List.of()));

        HermesVersionAdvisorSkill skill =
                new HermesVersionAdvisorSkill(gitLogReader, new RuleBasedHermesVersionAdvisorSynthesizer());

        HermesVersionAdvisorResult result = skill.run("C:/repos/hermes-agent", null, null, List.of());

        assertThat(result.recommendedRef()).isEqualTo("already up to date");
    }

    @Test
    void ruleBasedFallbackSurfacesFeatureCommitsWithoutJudgingThem() {
        GitLogReader gitLogReader = mock(GitLogReader.class);
        when(gitLogReader.readFacts("C:/repos/hermes-agent", "origin/main", "main", List.of()))
                .thenReturn(new GitLogReader.GitFacts(10, 0, "v2026.8.3", List.of(), List.of(), List.of(
                        new GitLogReader.TouchedCommit("aaa1111", "feat(agent): one-shot LLM helper"),
                        new GitLogReader.TouchedCommit("bbb2222", "feat(gateway): key Discord auto-thread sessions"))));

        HermesVersionAdvisorSkill skill =
                new HermesVersionAdvisorSkill(gitLogReader, new RuleBasedHermesVersionAdvisorSynthesizer());

        HermesVersionAdvisorResult result = skill.run("C:/repos/hermes-agent", null, null, List.of());

        assertThat(result.featureCommits()).hasSize(2);
        assertThat(result.suggestedEnhancements()).hasSize(1);
        assertThat(result.suggestedEnhancements().get(0)).contains("No AI provider configured", "one-shot LLM helper");
    }
}
