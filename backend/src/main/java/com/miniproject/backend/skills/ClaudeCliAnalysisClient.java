package com.miniproject.backend.skills;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Shells out to the Claude Code CLI already installed and logged in on this
 * machine, instead of calling a cloud API directly — no API key for this app
 * to manage at all, same "single-operator local tool" trust model already
 * used by GraphifyIndexService's ProcessBuilder call. Same AiAnalysisClient
 * contract as OpenAiAnalysisClient/LocalLlmAnalysisClient; only requires
 * `claude` on PATH and an existing login — that's the trade-off versus the
 * other two (works with zero key management, but ties analysis to whatever
 * machine runs this backend, which is exactly this project's scope).
 *
 * The prompt is written to the subprocess's stdin (`claude -p` with no
 * trailing argument) rather than passed as a command-line argument.
 * Discovered the hard way: on Windows this command runs through `cmd.exe /c`
 * (see commandFor()), and a multi-line prompt embedded as a quoted argument
 * gets mangled by cmd.exe's own parsing — piping it through stdin sidesteps
 * shell quoting entirely, confirmed against the real CLI.
 */
@Component
@Primary
@ConditionalOnProperty(name = "analysis.llm.provider", havingValue = "claude-cli")
public class ClaudeCliAnalysisClient implements AiAnalysisClient {

    private final String command;
    private final long timeoutSeconds;

    public ClaudeCliAnalysisClient(
            @Value("${claude-cli.command:claude}") String command,
            @Value("${claude-cli.timeout-seconds:120}") long timeoutSeconds) {
        this.command = (command == null || command.isBlank()) ? "claude" : command.trim();
        this.timeoutSeconds = timeoutSeconds <= 0 ? 120 : timeoutSeconds;
    }

    @Override
    public Optional<String> analyze(String systemPrompt, String userPrompt) {
        ProcessBuilder builder = new ProcessBuilder(commandFor());
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();

            // Drain stdout on a separate thread *while* the process runs —
            // reading only after waitFor() risks a classic deadlock if output
            // exceeds the OS pipe buffer (the process blocks writing, we
            // block waiting, neither moves).
            StringBuilder output = new StringBuilder();
            Thread reader = new Thread(() -> {
                try (var stream = process.getInputStream()) {
                    output.append(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                } catch (IOException ignored) {
                    // best-effort; exit code / timeout below decide the outcome
                }
            });
            reader.start();

            try (OutputStream stdin = process.getOutputStream()) {
                stdin.write(combinedPrompt(systemPrompt, userPrompt).getBytes(StandardCharsets.UTF_8));
                stdin.flush();
            }

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                reader.interrupt();
                return Optional.empty();
            }
            reader.join(Duration.ofSeconds(5).toMillis());

            if (process.exitValue() != 0 || output.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(output.toString().trim());
        } catch (IOException e) {
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    static String combinedPrompt(String systemPrompt, String userPrompt) {
        return (systemPrompt == null || systemPrompt.isBlank())
                ? userPrompt
                : systemPrompt.trim() + "\n\n" + userPrompt;
    }

    /**
     * Exposed for tests: the exact process argv this client would run
     * (prompt text goes over stdin, not here — see class javadoc). On
     * Windows, `claude` is typically a `claude.cmd` shell wrapper (npm global
     * install convention) — Java's ProcessBuilder does not reliably resolve
     * bare `.cmd`/`.bat` names the way a real shell does, so this routes
     * through `cmd.exe /c` there, same as typing the command into an actual
     * Windows terminal. Everywhere else, PATH-based executable resolution
     * already works without a shell in between.
     */
    List<String> commandFor() {
        if (!isWindows()) {
            return List.of(command, "-p");
        }
        return List.of("cmd.exe", "/c", command, "-p");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
