package com.miniproject.backend.web;

import com.miniproject.backend.skills.HermesSetupWizardAnswers;

import java.util.List;

public record HermesSetupWizardRequest(
        String profile,
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

    public HermesSetupWizardAnswers toAnswers() {
        return new HermesSetupWizardAnswers(
                repoPath, platforms, discordChannelId, emailImapHost, emailAccount, emailAllowedSenders,
                incidentReportsDir, incidentExtractsDir, incidentDownloadsDir, serverLogPath,
                prPackageEnabled != null && prPackageEnabled, gitHost);
    }
}
