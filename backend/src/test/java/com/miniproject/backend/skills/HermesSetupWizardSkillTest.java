package com.miniproject.backend.skills;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HermesSetupWizardSkillTest {

    private final HermesSetupWizardSkill skill = new HermesSetupWizardSkill(new RuleBasedHermesSetupWizardSynthesizer());

    @Test
    void rejectsBlankRepoPath() {
        assertThatThrownBy(() -> skill.run(answers(" ", List.of("discord"), false)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("repoPath");
    }

    @Test
    void generatesDiscordSkeletonWithRealChannelIdAndSecretPlaceholder() {
        HermesSetupWizardAnswers answers = new HermesSetupWizardAnswers(
                "C:/repos/example", List.of("discord"), "123456789", null, null, List.of(),
                null, null, null, null, false, null);

        HermesSetupWizardResult result = skill.run(answers);

        assertThat(result.generatedYaml()).contains("bot_token", "<FILL_IN_DISCORD_BOT_TOKEN>");
        assertThat(result.generatedYaml()).contains("intake_channel_id: \"123456789\"");
        assertThat(result.checklist()).anyMatch(item -> item.contains("Discord bot token"));
        assertThat(result.checklist()).noneMatch(item -> item.contains("git host credentials"));
    }

    @Test
    void warnsWhenEmailHasNoAllowedSenders() {
        HermesSetupWizardAnswers answers = new HermesSetupWizardAnswers(
                "C:/repos/example", List.of("email"), null, "imap.example.com", "bot@example.com", List.of(),
                null, null, null, null, false, null);

        HermesSetupWizardResult result = skill.run(answers);

        assertThat(result.generatedYaml()).contains("allowed_senders:", "<FILL_IN_ALLOWED_SENDER>");
        assertThat(result.checklist()).anyMatch(item -> item.contains("allowed senders"));
    }

    @Test
    void fillsRealAllowedSendersWhenGiven() {
        HermesSetupWizardAnswers answers = new HermesSetupWizardAnswers(
                "C:/repos/example", List.of("email"), null, "imap.example.com", "bot@example.com",
                List.of("alice@example.com", "bob@example.com"), null, null, null, null, false, null);

        HermesSetupWizardResult result = skill.run(answers);

        assertThat(result.generatedYaml()).contains("alice@example.com", "bob@example.com");
        assertThat(result.checklist()).noneMatch(item -> item.contains("allowed senders"));
    }

    @Test
    void supportsBothPlatformsAtOnce() {
        HermesSetupWizardAnswers answers = new HermesSetupWizardAnswers(
                "C:/repos/example", List.of("discord", "email"), "123", "imap.example.com", "bot@example.com",
                List.of("alice@example.com"), null, null, null, null, false, null);

        HermesSetupWizardResult result = skill.run(answers);

        assertThat(result.generatedYaml()).contains("discord:", "email:");
    }

    @Test
    void fillsRealDirectoryAndServerLogPathAnswers() {
        HermesSetupWizardAnswers answers = new HermesSetupWizardAnswers(
                "C:/repos/example", List.of("discord"), "123", null, null, List.of(),
                "D:/Hermes/incident-reports", "D:/Hermes/incident-extracts", "D:/OneDrive/logs",
                "\\\\fileserver\\logs", false, null);

        HermesSetupWizardResult result = skill.run(answers);

        assertThat(result.generatedYaml())
                .contains("D:/Hermes/incident-reports", "D:/Hermes/incident-extracts", "D:/OneDrive/logs", "\\\\fileserver\\logs");
        assertThat(result.checklist()).noneMatch(item -> item.contains("incident-reports directory"));
    }

    @Test
    void addsPrPackageChecklistItemsWhenEnabled() {
        HermesSetupWizardAnswers answers = new HermesSetupWizardAnswers(
                "C:/repos/example", List.of("email"), null, "imap.example.com", "bot@example.com",
                List.of("alice@example.com"), null, null, null, null, true, "bitbucket.org/org/repo");

        HermesSetupWizardResult result = skill.run(answers);

        assertThat(result.generatedYaml()).contains("pr_package:", "enabled: true", "bitbucket.org/org/repo");
        assertThat(result.checklist()).anyMatch(item -> item.contains("git host credentials"));
    }

    private HermesSetupWizardAnswers answers(String repoPath, List<String> platforms, boolean prPackageEnabled) {
        return new HermesSetupWizardAnswers(
                repoPath, platforms, null, null, null, List.of(), null, null, null, null, prPackageEnabled, null);
    }
}
