package com.miniproject.backend.integrations;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Gathers git facts for HermesVersionAdvisorSkill — the same commands used
 * manually to diagnose the real hermes-agent clone earlier (5212 commits
 * behind, exactly 3 touching the watched adapter.py path), now automated.
 * Plain `git` resolves directly via ProcessBuilder on Windows (unlike
 * `claude`, no .cmd-wrapper indirection needed — see ClaudeCliAnalysisClient),
 * but reuses its drain-thread + timeout pattern since `git fetch` is
 * network-bound and can hang.
 */
@Component
public class GitLogReader {

    private final long timeoutSeconds;

    public GitLogReader(@Value("${git-log-reader.timeout-seconds:120}") long timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds <= 0 ? 120 : timeoutSeconds;
    }

    public record TouchedCommit(String commit, String subject) {
    }

    public record GitFacts(
            int commitsBehind,
            int commitsAhead,
            String latestTag,
            List<String> watchedPaths,
            List<TouchedCommit> touchedWatchedFiles,
            List<TouchedCommit> featureCommits) {
    }

    /**
     * @param remoteRef the upstream ref to compare against, e.g. "origin/main"
     * @param localRef  the local ref, e.g. "main" or "HEAD"
     * @param watchedPaths repo-relative paths whose upstream history matters (patched files) --
     *                     pass null/empty to auto-detect via detectChangedFiles() instead of
     *                     requiring the analyst to already know which files they customized.
     */
    public GitFacts readFacts(String repoPath, String remoteRef, String localRef, List<String> watchedPaths) {
        run(repoPath, "fetch");

        String counts = run(repoPath, "rev-list", "--left-right", "--count", remoteRef + "..." + localRef);
        int[] parsed = parseCounts(counts);

        String tagOutput = run(repoPath, "tag", "--merged", remoteRef, "--sort=-creatordate");
        String latestTag = tagOutput.lines().filter(line -> !line.isBlank()).findFirst().orElse(null);

        List<String> effectiveWatchedPaths = (watchedPaths != null && !watchedPaths.isEmpty())
                ? watchedPaths
                : detectChangedFiles(repoPath, remoteRef, localRef);

        List<TouchedCommit> touched = new ArrayList<>();
        if (!effectiveWatchedPaths.isEmpty()) {
            List<String> args = new ArrayList<>(List.of("log", "--oneline", localRef + ".." + remoteRef, "--"));
            args.addAll(effectiveWatchedPaths);
            String log = run(repoPath, args.toArray(String[]::new));
            touched.addAll(parseOnelineLog(log));
        }

        // Only feat:-prefixed subjects, not every commit -- hermes-agent's history
        // is thousands of commits deep and uses this conventional-commit prefix
        // consistently, so this keeps the candidate list small enough to actually
        // hand to an LLM (a few hundred, not several thousand).
        String featureLog = run(repoPath, "log", "--oneline", localRef + ".." + remoteRef, "--grep=^feat", "-i");
        List<TouchedCommit> featureCommits = parseOnelineLog(featureLog);

        return new GitFacts(parsed[0], parsed[1], latestTag, effectiveWatchedPaths, touched, featureCommits);
    }

    /**
     * Files that differ between localRef and where it diverged from remoteRef
     * (three-dot diff against the merge-base) -- i.e. "what have we actually
     * changed locally", without the analyst needing to already know or type
     * file paths themselves.
     */
    public List<String> detectChangedFiles(String repoPath, String remoteRef, String localRef) {
        String output = run(repoPath, "diff", "--name-only", remoteRef + "..." + localRef);
        return output.lines().map(String::trim).filter(line -> !line.isBlank()).toList();
    }

    /** True when `git status --porcelain` reports nothing -- the safety gate before pull(). */
    public boolean isWorkingTreeClean(String repoPath) {
        return run(repoPath, "status", "--porcelain").isBlank();
    }

    /**
     * Runs a plain `git pull` on whatever branch is currently checked out.
     * Callers MUST verify isWorkingTreeClean() first -- this does not check
     * or stash anything itself. See HermesVersionControlService for the
     * review-gate + clean-check wrapping around this.
     */
    public String pull(String repoPath) {
        return run(repoPath, "pull");
    }

    private List<TouchedCommit> parseOnelineLog(String log) {
        List<TouchedCommit> commits = new ArrayList<>();
        for (String line : log.lines().toList()) {
            if (line.isBlank()) {
                continue;
            }
            int space = line.indexOf(' ');
            if (space > 0) {
                commits.add(new TouchedCommit(line.substring(0, space), line.substring(space + 1).trim()));
            }
        }
        return commits;
    }

    private int[] parseCounts(String output) {
        String trimmed = output.trim();
        if (trimmed.isEmpty()) {
            return new int[] {0, 0};
        }
        String[] parts = trimmed.split("\\s+");
        try {
            int behind = parts.length > 0 ? Integer.parseInt(parts[0]) : 0;
            int ahead = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            return new int[] {behind, ahead};
        } catch (NumberFormatException e) {
            return new int[] {0, 0};
        }
    }

    private String run(String repoPath, String... gitArgs) {
        List<String> command = new ArrayList<>(List.of("git", "-C", repoPath));
        command.addAll(List.of(gitArgs));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();

            StringBuilder output = new StringBuilder();
            Thread reader = new Thread(() -> {
                try (var stream = process.getInputStream()) {
                    output.append(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                } catch (IOException ignored) {
                    // best-effort; exit code / timeout below decide the outcome
                }
            });
            reader.start();

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                reader.interrupt();
                throw new GitLogReaderException("git " + String.join(" ", gitArgs) + " timed out after " + timeoutSeconds + "s");
            }
            reader.join(Duration.ofSeconds(5).toMillis());

            if (process.exitValue() != 0) {
                throw new GitLogReaderException(
                        "git " + String.join(" ", gitArgs) + " exited " + process.exitValue() + ": " + output);
            }
            return output.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitLogReaderException("Interrupted running git " + String.join(" ", gitArgs));
        }
    }

    public static class GitLogReaderException extends RuntimeException {
        public GitLogReaderException(String message) {
            super(message);
        }
    }
}
