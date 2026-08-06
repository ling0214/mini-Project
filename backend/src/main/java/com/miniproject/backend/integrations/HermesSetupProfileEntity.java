package com.miniproject.backend.integrations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Stores the Setup Wizard's Q&A *answers* themselves (not the generated
 * YAML, which is re-derived on demand) so a saved profile can be reloaded
 * and edited instead of re-answering every question from scratch each time.
 * platforms/emailAllowedSenders are comma-separated in a single column
 * rather than a @ElementCollection join table -- matches this codebase's
 * existing preference for simple columns over extra tables for small lists.
 */
@Entity
@Table(name = "hermes_setup_profiles")
public class HermesSetupProfileEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "repo_path", nullable = false)
    private String repoPath;

    @Column(name = "platforms")
    private String platforms;

    @Column(name = "discord_channel_id")
    private String discordChannelId;

    @Column(name = "email_imap_host")
    private String emailImapHost;

    @Column(name = "email_account")
    private String emailAccount;

    @Lob
    @Column(name = "email_allowed_senders")
    private String emailAllowedSenders;

    @Column(name = "incident_reports_dir")
    private String incidentReportsDir;

    @Column(name = "incident_extracts_dir")
    private String incidentExtractsDir;

    @Column(name = "incident_downloads_dir")
    private String incidentDownloadsDir;

    @Column(name = "server_log_path")
    private String serverLogPath;

    @Column(name = "pr_package_enabled", nullable = false)
    private boolean prPackageEnabled;

    @Column(name = "git_host")
    private String gitHost;

    /**
     * The folder that directly contains Hermes's own "incidents" and
     * "agent-tasks" for whichever Hermes install serves this project --
     * separate from the generated setup YAML (Hermes doesn't need to be told
     * its own home directory), this is purely so mini-Project's Production
     * Incidents panel can follow the active project instead of one global
     * path. See HermesIncidentReader.detectHermesHome for the auto-detect
     * this can be seeded from.
     */
    @Column(name = "hermes_home")
    private String hermesHome;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HermesSetupProfileEntity() {
        // JPA
    }

    public HermesSetupProfileEntity(String id, String name) {
        this.id = id == null || id.isBlank() ? UUID.randomUUID().toString() : id;
        this.name = name;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void applyAnswers(
            String repoPath, List<String> platforms, String discordChannelId,
            String emailImapHost, String emailAccount, List<String> emailAllowedSenders,
            String incidentReportsDir, String incidentExtractsDir, String incidentDownloadsDir,
            String serverLogPath, boolean prPackageEnabled, String gitHost, String hermesHome) {
        this.repoPath = repoPath;
        this.platforms = join(platforms);
        this.discordChannelId = discordChannelId;
        this.emailImapHost = emailImapHost;
        this.emailAccount = emailAccount;
        this.emailAllowedSenders = join(emailAllowedSenders);
        this.incidentReportsDir = incidentReportsDir;
        this.incidentExtractsDir = incidentExtractsDir;
        this.incidentDownloadsDir = incidentDownloadsDir;
        this.serverLogPath = serverLogPath;
        this.prPackageEnabled = prPackageEnabled;
        this.gitHost = gitHost;
        this.hermesHome = hermesHome;
        this.updatedAt = Instant.now();
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return String.join(",", values);
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split(",")).stream().map(String::trim).filter(s -> !s.isBlank()).toList();
    }

    public String getId() {
        return id;
    }

    public HermesSetupProfileView toView() {
        return new HermesSetupProfileView(
                id, name, repoPath, split(platforms), discordChannelId, emailImapHost, emailAccount,
                split(emailAllowedSenders), incidentReportsDir, incidentExtractsDir, incidentDownloadsDir,
                serverLogPath, prPackageEnabled, gitHost, hermesHome, createdAt.toString(), updatedAt.toString());
    }
}
