package com.miniproject.backend.skills;

import com.miniproject.backend.artifact.Evidence;

import java.util.List;

/** Weekly digest of GitHub Trending top-N repos, with an AI relevance verdict per candidate. */
public record HermesTrendingDigestResult(List<Candidate> candidates, List<Evidence> evidence) {

    public record Candidate(String repoName, String description, String stars, boolean relevant, String reasoning) {
    }
}
