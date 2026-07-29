package com.miniproject.backend.integrations;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase 1 Jira intake: dry-run/sample importer. It gives the workflow a
 * Jira-shaped entry point without requiring OAuth/API credentials yet.
 */
@Service
public class JiraTicketImportService {

    private static final Pattern ISSUE_KEY = Pattern.compile("([A-Z][A-Z0-9]+-\\d+)");
    private final JiraConnector jiraConnector;

    @Autowired
    public JiraTicketImportService(JiraConnector jiraConnector) {
        this.jiraConnector = jiraConnector;
    }

    JiraTicketImportService() {
        this.jiraConnector = null;
    }

    public JiraTicketImportResponse importTicket(JiraTicketImportRequest request) {
        String key = extractKey(request);
        if (key.isBlank()) {
            throw new IllegalArgumentException("ticketKey or ticketUrl is required");
        }

        if (jiraConnector != null && jiraConnector.canReadIssues()) {
            return realJiraImport(jiraConnector.fetchIssue(key));
        }

        if ("MBC-204".equalsIgnoreCase(key)) {
            return new JiraTicketImportResponse(
                    "MBC-204",
                    "Allow donors to filter available aid requests by city and urgency",
                    "High",
                    "FYP Supervisor",
                    "Donor should be able to filter approved aid request records by city, category, and urgency before responding to help.",
                    "Given a donor is browsing available aid requests, when the donor selects city, category, or urgency filters, then the page only shows matching approved aid requests.",
                    "Use page reload first. AJAX filtering can be treated as a future enhancement unless the stakeholder confirms it is required now.",
                    "jira-dry-run",
                    "Jira",
                    "Jira sample import",
                    "https://jira.example.local/browse/MBC-204",
                    "Sample ticket",
                    true,
                    "Dry-run Jira import loaded from the MyBanjirCare sample ticket.");
        }

        return new JiraTicketImportResponse(
                key.toUpperCase(Locale.ROOT),
                "Imported Jira ticket " + key.toUpperCase(Locale.ROOT),
                "Medium",
                "Stakeholder",
                "Paste or replace this dry-run description with the real Jira ticket description before analysis.",
                "Add acceptance criteria from Jira before marking the requirement as reviewed.",
                "Dry-run importer only. Configure real Jira read access in the next phase.",
                "jira-dry-run",
                "Jira",
                "Jira placeholder import",
                "",
                "Dry-run placeholder",
                true,
                "Dry-run Jira import created a placeholder because this key is not in the sample set.");
    }

    private JiraTicketImportResponse realJiraImport(JiraConnector.JiraIssue issue) {
        return new JiraTicketImportResponse(
                issue.key(),
                issue.title(),
                defaultIfBlank(issue.priority(), "Medium"),
                defaultIfBlank(issue.reporter(), "Jira"),
                issue.description(),
                issue.acceptanceCriteria(),
                issue.comments(),
                "jira",
                "Jira",
                "Jira read-only import",
                issue.sourceUrl(),
                issue.receivedAt(),
                false,
                "Imported live Jira ticket " + issue.key() + ".");
    }

    private static String extractKey(JiraTicketImportRequest request) {
        if (request == null) {
            return "";
        }
        String explicitKey = clean(request.ticketKey());
        if (!explicitKey.isBlank()) {
            return explicitKey;
        }
        Matcher matcher = ISSUE_KEY.matcher(clean(request.ticketUrl()).toUpperCase(Locale.ROOT));
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private static String defaultIfBlank(String value, String fallback) {
        String cleaned = clean(value);
        return cleaned.isBlank() ? fallback : cleaned;
    }
}
