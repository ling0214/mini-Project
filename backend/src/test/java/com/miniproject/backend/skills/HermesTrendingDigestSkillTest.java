package com.miniproject.backend.skills;

import com.miniproject.backend.integrations.GitHubTrendingFetcher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HermesTrendingDigestSkillTest {

    @Test
    void ruleBasedFallbackSurfacesEveryCandidateAsUnjudged() {
        GitHubTrendingFetcher fetcher = mock(GitHubTrendingFetcher.class);
        when(fetcher.fetchTop(3)).thenReturn(List.of(
                new GitHubTrendingFetcher.TrendingRepo("owner/repo-a", "Does a thing", "1,234"),
                new GitHubTrendingFetcher.TrendingRepo("owner/repo-b", "Does another thing", "500")));

        HermesTrendingDigestSkill skill =
                new HermesTrendingDigestSkill(fetcher, new RuleBasedTrendingRelevanceSynthesizer());

        HermesTrendingDigestResult result = skill.run();

        assertThat(result.candidates()).hasSize(2);
        assertThat(result.candidates()).allMatch(candidate -> !candidate.relevant());
        assertThat(result.candidates().get(0).repoName()).isEqualTo("owner/repo-a");
        assertThat(result.candidates().get(0).reasoning()).contains("No AI provider configured");
    }

    @Test
    void returnsEmptyCandidatesWhenNothingFetched() {
        GitHubTrendingFetcher fetcher = mock(GitHubTrendingFetcher.class);
        when(fetcher.fetchTop(3)).thenReturn(List.of());

        HermesTrendingDigestSkill skill =
                new HermesTrendingDigestSkill(fetcher, new RuleBasedTrendingRelevanceSynthesizer());

        HermesTrendingDigestResult result = skill.run();

        assertThat(result.candidates()).isEmpty();
    }
}
