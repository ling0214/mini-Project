package com.miniproject.backend.skills;

import com.miniproject.backend.artifact.Evidence;
import com.miniproject.backend.integrations.GitLogReader;

import java.util.List;

/**
 * "Should we adopt this upstream version, and why" — not a blind git pull.
 * Reviewing/accepting this result (via the normal Artifact review-gate) is
 * enough to produce a memory card for the next version-advisor run, via the
 * existing generic MemoryCardService.recordReviewed() mechanism — no new
 * memory infrastructure needed. Once reviewed, HermesVersionControlService
 * allows the actual `git pull` for this repo (see its own clean-working-tree
 * gate) — this is the "control" half, not just "advisor".
 */
public record HermesVersionAdvisorResult(
        String repoPath,
        int commitsBehind,
        int commitsAhead,
        String latestTag,
        List<String> watchedPaths,
        List<GitLogReader.TouchedCommit> touchedWatchedFiles,
        List<GitLogReader.TouchedCommit> featureCommits,
        String recommendedRef,
        String rationale,
        List<String> risksIfAdopted,
        List<String> suggestedEnhancements,
        List<Evidence> evidence) {
}
