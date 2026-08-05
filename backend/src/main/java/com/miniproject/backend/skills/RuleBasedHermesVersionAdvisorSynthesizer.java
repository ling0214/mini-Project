package com.miniproject.backend.skills;

import com.miniproject.backend.artifact.Evidence;
import com.miniproject.backend.integrations.GitLogReader;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic fallback: no reasoning about the CONTENT of commits, only
 * whether any touch the watched (patched) files. Safe-by-construction — it
 * never recommends adopting past a commit that touches a watched file
 * without flagging it for human review first.
 */
@Component
public class RuleBasedHermesVersionAdvisorSynthesizer implements HermesVersionAdvisorSynthesizer {

    @Override
    public HermesVersionAdvisorResult synthesize(String repoPath, GitLogReader.GitFacts facts) {
        String recommendedRef;
        String rationale;
        List<String> risks = new ArrayList<>();

        if (facts.commitsBehind() == 0) {
            recommendedRef = "already up to date";
            rationale = "Local is already at or ahead of the upstream ref — nothing to adopt.";
        } else if (facts.touchedWatchedFiles().isEmpty()) {
            recommendedRef = facts.latestTag() != null ? facts.latestTag() : "upstream HEAD";
            rationale = "%d commit(s) behind upstream, but none touch the watched/patched files — safe to adopt."
                    .formatted(facts.commitsBehind());
        } else {
            recommendedRef = "manual review required before adopting";
            rationale = "%d of the %d commit(s) behind upstream touch watched/patched file(s) — review each before adopting."
                    .formatted(facts.touchedWatchedFiles().size(), facts.commitsBehind());
            for (GitLogReader.TouchedCommit commit : facts.touchedWatchedFiles()) {
                risks.add(commit.commit() + ": " + commit.subject());
            }
        }

        List<Evidence> evidence = List.of(new Evidence(
                "%d commits behind, %d touching watched files, %d feat: commits found"
                        .formatted(facts.commitsBehind(), facts.touchedWatchedFiles().size(), facts.featureCommits().size()),
                "hermes-version-advisor (rule-based)"));

        // Honest fallback, same pattern as RuleBasedTrendingRelevanceSynthesizer:
        // judging which features are actually *valuable* requires reasoning this
        // has none of -- surface the raw candidates instead of fabricating a verdict.
        List<String> suggestedEnhancements = facts.featureCommits().isEmpty()
                ? List.of()
                : List.of("No AI provider configured — %d feat: commit(s) found upstream but not evaluated for relevance. Candidates: %s"
                        .formatted(
                                facts.featureCommits().size(),
                                facts.featureCommits().stream().map(GitLogReader.TouchedCommit::subject).limit(5)
                                        .reduce((a, b) -> a + "; " + b).orElse("")));

        return new HermesVersionAdvisorResult(
                repoPath,
                facts.commitsBehind(),
                facts.commitsAhead(),
                facts.latestTag(),
                facts.watchedPaths(),
                facts.touchedWatchedFiles(),
                facts.featureCommits(),
                recommendedRef,
                rationale,
                risks,
                suggestedEnhancements,
                evidence);
    }
}
