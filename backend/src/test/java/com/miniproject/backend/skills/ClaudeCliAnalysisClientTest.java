package com.miniproject.backend.skills;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ClaudeCliAnalysisClientTest {

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static List<String> expectedCommand(String command) {
        return isWindows() ? List.of("cmd.exe", "/c", command, "-p") : List.of(command, "-p");
    }

    @Test
    void defaultsBlankCommandToClaudeAndNonPositiveTimeoutTo120() {
        ClaudeCliAnalysisClient client = new ClaudeCliAnalysisClient("  ", 0);

        assertThat(client.commandFor()).containsExactlyElementsOf(expectedCommand("claude"));
    }

    @Test
    void usesConfiguredCommandName() {
        ClaudeCliAnalysisClient client = new ClaudeCliAnalysisClient("claude-beta", 60);

        assertThat(client.commandFor()).containsExactlyElementsOf(expectedCommand("claude-beta"));
    }

    @Test
    void combinesSystemAndUserPromptWithBlankLineSeparator() {
        assertThat(ClaudeCliAnalysisClient.combinedPrompt("Be concise.", "What does X do?"))
                .isEqualTo("Be concise.\n\nWhat does X do?");
    }

    @Test
    void omitsSystemPromptWhenBlank() {
        assertThat(ClaudeCliAnalysisClient.combinedPrompt("", "What does X do?")).isEqualTo("What does X do?");
        assertThat(ClaudeCliAnalysisClient.combinedPrompt(null, "What does X do?")).isEqualTo("What does X do?");
    }

    /**
     * Real end-to-end check against the actual claude CLI installed on this
     * machine — skipped by default (costs a real call) since there's no way
     * to mock Process/ProcessBuilder cleanly. Run with -Dclaudecli.smoke=true.
     */
    @Test
    void liveSmokeTestAgainstRealClaudeCli() {
        assumeTrue(Boolean.getBoolean("claudecli.smoke"), "Run with -Dclaudecli.smoke=true to exercise the real claude CLI");

        ClaudeCliAnalysisClient client = new ClaudeCliAnalysisClient("claude", 60);
        var answer = client.analyze("Reply with exactly one word.", "Say hello.");

        assertThat(answer).isPresent();
        assertThat(answer.get()).isNotBlank();
    }
}
