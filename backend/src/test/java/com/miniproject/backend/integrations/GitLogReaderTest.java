package com.miniproject.backend.integrations;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real git commands against a throwaway temp repo (not mocks) -- this is the
 * genuinely new, previously-untested part: detectChangedFiles()'s three-dot
 * diff syntax. Sets local user.name/user.email in the temp repo itself so
 * this doesn't depend on global git config being present.
 */
class GitLogReaderTest {

    private final GitLogReader gitLogReader = new GitLogReader(30);

    @TempDir
    Path tempDir;

    @Test
    void detectChangedFilesReturnsOnlyFilesTouchedSinceDivergingFromUpstream() throws Exception {
        Path upstream = tempDir.resolve("upstream");
        Path clone = tempDir.resolve("clone");
        Files.createDirectories(upstream);

        git(upstream, "init", "-b", "main");
        configureIdentity(upstream);
        Files.writeString(upstream.resolve("shared.txt"), "shared\n");
        Files.writeString(upstream.resolve("untouched.txt"), "never changes\n");
        git(upstream, "add", ".");
        git(upstream, "commit", "-m", "initial commit");

        git(tempDir, "clone", upstream.toString(), clone.toString());
        configureIdentity(clone);

        // Upstream gains a commit the clone never sees (so commitsBehind > 0 too).
        Files.writeString(upstream.resolve("untouched.txt"), "upstream changed this, not us\n");
        git(upstream, "commit", "-am", "feat: upstream-only change");

        // The clone independently changes a *different* file -- this is what
        // detectChangedFiles() must surface as the watched path.
        Files.writeString(clone.resolve("shared.txt"), "shared\nlocally patched\n");
        git(clone, "commit", "-am", "our local patch");

        git(clone, "fetch", "origin");

        List<String> changed = gitLogReader.detectChangedFiles(clone.toString(), "origin/main", "main");

        assertThat(changed).containsExactly("shared.txt");
    }

    @Test
    void readFactsAutoDetectsWatchedPathsWhenNoneGiven() throws Exception {
        Path upstream = tempDir.resolve("upstream2");
        Path clone = tempDir.resolve("clone2");
        Files.createDirectories(upstream);

        git(upstream, "init", "-b", "main");
        configureIdentity(upstream);
        Files.writeString(upstream.resolve("app.py"), "print('v1')\n");
        git(upstream, "add", ".");
        git(upstream, "commit", "-m", "initial commit");

        git(tempDir, "clone", upstream.toString(), clone.toString());
        configureIdentity(clone);

        Files.writeString(upstream.resolve("app.py"), "print('v2 from upstream')\n");
        git(upstream, "commit", "-am", "feat: upstream update to app.py");

        Files.writeString(clone.resolve("local_only.py"), "print('our own file')\n");
        git(clone, "add", ".");
        git(clone, "commit", "-m", "add local-only file");

        git(clone, "fetch", "origin");

        GitLogReader.GitFacts facts = gitLogReader.readFacts(clone.toString(), "origin/main", "main", null);

        assertThat(facts.watchedPaths()).containsExactly("local_only.py");
        // app.py isn't a watched path (we never touched it), so it must not appear as a risk.
        assertThat(facts.touchedWatchedFiles()).isEmpty();
        assertThat(facts.featureCommits()).hasSize(1);
        assertThat(facts.featureCommits().get(0).subject()).contains("upstream update to app.py");
    }

    private void configureIdentity(Path repo) throws IOException, InterruptedException {
        git(repo, "config", "user.email", "test@example.com");
        git(repo, "config", "user.name", "Test User");
    }

    @Test
    void toWebUrlStripsGitSuffixFromHttpsUrl() {
        String input = "https://github.com/NousResearch/hermes-agent.git";
        String output = GitLogReader.toWebUrl(input);
        assertThat(output).isEqualTo("https://github.com/NousResearch/hermes-agent");
    }

    @Test
    void toWebUrlPreservesHttpsUrlWithoutGitSuffix() {
        String input = "https://github.com/NousResearch/hermes-agent";
        String output = GitLogReader.toWebUrl(input);
        assertThat(output).isEqualTo("https://github.com/NousResearch/hermes-agent");
    }

    @Test
    void toWebUrlConvertsGitAtSyntaxToHttps() {
        String input = "git@github.com:NousResearch/hermes-agent.git";
        String output = GitLogReader.toWebUrl(input);
        assertThat(output).isEqualTo("https://github.com/NousResearch/hermes-agent");
    }

    @Test
    void toWebUrlConvertsSshSyntaxToHttps() {
        String input = "ssh://git@github.com/NousResearch/hermes-agent.git";
        String output = GitLogReader.toWebUrl(input);
        assertThat(output).isEqualTo("https://github.com/NousResearch/hermes-agent");
    }

    @Test
    void toWebUrlReturnsNullForUnrecognizedScheme() {
        String input = "file:///local/path/repo.git";
        String output = GitLogReader.toWebUrl(input);
        assertThat(output).isNull();
    }

    @Test
    void toWebUrlReturnsNullForNull() {
        String output = GitLogReader.toWebUrl(null);
        assertThat(output).isNull();
    }

    @Test
    void toWebUrlReturnsNullForBlank() {
        String output = GitLogReader.toWebUrl("   ");
        assertThat(output).isNull();
    }

    @Test
    void getRemoteUrlReturnsConfiguredOriginByDefault() throws Exception {
        Path repo = tempDir.resolve("remote-url-test");
        Files.createDirectories(repo);

        git(repo, "init", "-b", "main");
        configureIdentity(repo);
        Files.writeString(repo.resolve("test.txt"), "test\n");
        git(repo, "add", ".");
        git(repo, "commit", "-m", "initial");

        // Configure a fake remote URL
        git(repo, "remote", "add", "origin", "https://github.com/test/repo.git");

        String url = gitLogReader.getRemoteUrl(repo.toString(), null);

        assertThat(url).isEqualTo("https://github.com/test/repo.git");
    }

    @Test
    void getRemoteUrlReturnsExplicitRemoteName() throws Exception {
        Path repo = tempDir.resolve("remote-url-named");
        Files.createDirectories(repo);

        git(repo, "init", "-b", "main");
        configureIdentity(repo);
        Files.writeString(repo.resolve("test.txt"), "test\n");
        git(repo, "add", ".");
        git(repo, "commit", "-m", "initial");

        git(repo, "remote", "add", "upstream", "https://github.com/upstream/repo.git");

        String url = gitLogReader.getRemoteUrl(repo.toString(), "upstream");

        assertThat(url).isEqualTo("https://github.com/upstream/repo.git");
    }

    @Test
    void getRemoteUrlThrowsWhenNotAGitRepo() {
        Path notARepo = tempDir.resolve("not-a-repo");
        try {
            Files.createDirectories(notARepo);
        } catch (IOException e) {
            throw new AssertionError("Failed to create temp dir", e);
        }

        GitLogReader.GitLogReaderException ex = org.junit.jupiter.api.Assertions.assertThrows(
                GitLogReader.GitLogReaderException.class,
                () -> gitLogReader.getRemoteUrl(notARepo.toString(), null)
        );

        assertThat(ex.getMessage()).contains("fatal");
    }

    private void git(Path cwd, String... args) throws IOException, InterruptedException {
        List<String> command = new java.util.ArrayList<>(List.of("git"));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command)
                .directory(cwd.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes());
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        if (!finished || process.exitValue() != 0) {
            throw new AssertionError("git " + String.join(" ", args) + " failed: " + output);
        }
    }
}
