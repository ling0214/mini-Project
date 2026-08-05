package com.miniproject.backend.integrations;

import com.miniproject.backend.artifact.Artifact;
import com.miniproject.backend.persistence.ArtifactPersistenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The "control" half of Hermes Version Control (not just "advisor") —
 * actually runs `git pull`, gated two ways: (1) the recommendation artifact
 * must be reviewed first (same Artifact review-gate every other skill uses),
 * and (2) the working tree must already be clean. This does NOT auto-commit
 * or auto-branch on the user's behalf — see the implementation plan's
 * decision log: the analyst commits their own in-flight changes first, this
 * service only checks and refuses if they haven't, exactly like
 * ExternalHandoffService requires review before an external handoff.
 */
@Service
public class HermesVersionControlService {

    private final ArtifactPersistenceService persistence;
    private final GitLogReader gitLogReader;

    public HermesVersionControlService(ArtifactPersistenceService persistence, GitLogReader gitLogReader) {
        this.persistence = persistence;
        this.gitLogReader = gitLogReader;
    }

    public HermesPullStatusView checkStatus(String repoPath) {
        boolean clean = gitLogReader.isWorkingTreeClean(repoPath);
        String message = clean
                ? "Working tree is clean — pull is allowed once the recommendation is reviewed."
                : "Working tree has uncommitted changes. Commit or stash them yourself first, then check again.";
        return new HermesPullStatusView(clean, message);
    }

    @Transactional
    public HermesPullResultView pull(String sourceTaskId, String repoPath) {
        Artifact<Object> source = persistence.findArtifact(sourceTaskId)
                .orElseThrow(() -> new IllegalArgumentException("No artifact found for task_id " + sourceTaskId));
        if (!source.reviewed()) {
            throw new IllegalArgumentException(
                    "Artifact " + sourceTaskId + " must be reviewed before pulling — approve the recommendation first.");
        }
        if (!gitLogReader.isWorkingTreeClean(repoPath)) {
            throw new IllegalArgumentException(
                    "Working tree has uncommitted changes — commit or stash them yourself first, then try again.");
        }
        String output = gitLogReader.pull(repoPath);
        return new HermesPullResultView(true, output);
    }

    public record HermesPullStatusView(boolean clean, String message) {
    }

    public record HermesPullResultView(boolean success, String output) {
    }
}
