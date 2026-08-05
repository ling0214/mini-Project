package com.miniproject.backend.skills;

import java.util.List;

/**
 * Shared shape between the wizard skill, its synthesizers, and
 * HermesSetupProfileEntity (integrations package) — one place for the field
 * list instead of a long, drifting parameter list in three places.
 */
public record HermesSetupWizardAnswers(
        String repoPath,
        List<String> platforms,
        String discordChannelId,
        String emailImapHost,
        String emailAccount,
        List<String> emailAllowedSenders,
        String incidentReportsDir,
        String incidentExtractsDir,
        String incidentDownloadsDir,
        String serverLogPath,
        boolean prPackageEnabled,
        String gitHost) {

    public boolean hasPlatform(String platform) {
        return platforms != null && platforms.stream().anyMatch(p -> p.equalsIgnoreCase(platform));
    }
}
