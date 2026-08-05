package com.miniproject.backend.web;

import java.util.List;

public record HermesSetupProfileRequest(
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
        Boolean prPackageEnabled,
        String gitHost) {
}
