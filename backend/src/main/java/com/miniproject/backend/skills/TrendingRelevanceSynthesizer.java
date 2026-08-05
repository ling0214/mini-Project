package com.miniproject.backend.skills;

import com.miniproject.backend.integrations.GitHubTrendingFetcher;

import java.util.List;

/** Same seam as the other *Synthesizer interfaces. Judges relevance to Hermes, not to mini-Project itself. */
public interface TrendingRelevanceSynthesizer {

    HermesTrendingDigestResult synthesize(List<GitHubTrendingFetcher.TrendingRepo> candidates);
}
