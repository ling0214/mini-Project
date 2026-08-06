package com.miniproject.backend.integrations;

import java.util.List;

public record HermesSetupProfileView(
        String id,
        String name,
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
        String gitHost,
        String hermesHome,
        String createdAt,
        String updatedAt) {
}
