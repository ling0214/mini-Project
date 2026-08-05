package com.miniproject.backend.skills;

import com.miniproject.backend.integrations.GitHubTrendingFetcher;
import org.springframework.stereotype.Component;

/**
 * Boss idea #2 ("daily scan of GitHub Trending top 3 for things useful to
 * Hermes") — see the mini-Project <-> Hermes implementation plan. Weekly,
 * not daily: trending shifts slowly enough that daily would just be noise.
 */
@Component
public class HermesTrendingDigestSkill {

    private static final int TOP_N = 3;

    private final GitHubTrendingFetcher fetcher;
    private final TrendingRelevanceSynthesizer synthesizer;

    public HermesTrendingDigestSkill(GitHubTrendingFetcher fetcher, TrendingRelevanceSynthesizer synthesizer) {
        this.fetcher = fetcher;
        this.synthesizer = synthesizer;
    }

    public HermesTrendingDigestResult run() {
        return synthesizer.synthesize(fetcher.fetchTop(TOP_N));
    }
}
