package com.miniproject.backend.skills;

import com.miniproject.backend.artifact.Evidence;
import com.miniproject.backend.integrations.GitHubTrendingFetcher;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Honest fallback: judging "is this repo relevant to Hermes" requires actual
 * reasoning about what the repo does, which this has none of — marks every
 * candidate as unjudged rather than fabricating a verdict. The LLM path is
 * what actually does this job; this exists so the digest degrades gracefully
 * (and testably) when no AI provider is configured.
 */
@Component
public class RuleBasedTrendingRelevanceSynthesizer implements TrendingRelevanceSynthesizer {

    @Override
    public HermesTrendingDigestResult synthesize(List<GitHubTrendingFetcher.TrendingRepo> candidates) {
        List<HermesTrendingDigestResult.Candidate> results = new ArrayList<>();
        for (GitHubTrendingFetcher.TrendingRepo repo : candidates) {
            results.add(new HermesTrendingDigestResult.Candidate(
                    repo.fullName(), repo.description(), repo.stars(), false,
                    "No AI provider configured — relevance not judged, candidate surfaced as-is for manual review."));
        }
        List<Evidence> evidence = List.of(
                new Evidence(results.size() + " trending candidate(s) surfaced", "hermes-trending-digest (rule-based)"));
        return new HermesTrendingDigestResult(results, evidence);
    }
}
