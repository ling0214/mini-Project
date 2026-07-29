package com.miniproject.backend.integrations;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JiraTicketImportServiceTest {

    private final JiraTicketImportService service = new JiraTicketImportService();

    @Test
    void importsMyBanjirCareSampleTicketByKey() {
        JiraTicketImportResponse response = service.importTicket(new JiraTicketImportRequest("MBC-204", null));

        assertThat(response.ticketKey()).isEqualTo("MBC-204");
        assertThat(response.ticketTitle()).contains("donors");
        assertThat(response.description()).contains("Donor should be able to filter");
        assertThat(response.sourceType()).isEqualTo("Jira");
        assertThat(response.sourceUrl()).contains("/browse/MBC-204");
        assertThat(response.dryRun()).isTrue();
    }

    @Test
    void importsLiveJiraTicketWhenConnectorIsConfigured() {
        JiraConnector jiraConnector = mock(JiraConnector.class);
        when(jiraConnector.canReadIssues()).thenReturn(true);
        when(jiraConnector.fetchIssue("MBC-900")).thenReturn(new JiraConnector.JiraIssue(
                "MBC-900",
                "Live Jira summary",
                "High",
                "Product Owner",
                "Live Jira description",
                "Live acceptance criteria",
                "Developer: Please confirm API behavior.",
                "https://example.atlassian.net/browse/MBC-900",
                "2026-07-29T09:00:00.000+0800"));

        JiraTicketImportService liveService = new JiraTicketImportService(jiraConnector);

        JiraTicketImportResponse response = liveService.importTicket(new JiraTicketImportRequest("MBC-900", null));

        assertThat(response.ticketKey()).isEqualTo("MBC-900");
        assertThat(response.ticketTitle()).isEqualTo("Live Jira summary");
        assertThat(response.acceptanceCriteria()).isEqualTo("Live acceptance criteria");
        assertThat(response.comments()).contains("Developer");
        assertThat(response.source()).isEqualTo("jira");
        assertThat(response.sourceUrl()).isEqualTo("https://example.atlassian.net/browse/MBC-900");
        assertThat(response.receivedAt()).isEqualTo("2026-07-29T09:00:00.000+0800");
        assertThat(response.dryRun()).isFalse();
    }

    @Test
    void extractsTicketKeyFromJiraUrl() {
        JiraTicketImportResponse response = service.importTicket(
                new JiraTicketImportRequest(null, "https://example.atlassian.net/browse/MBC-204"));

        assertThat(response.ticketKey()).isEqualTo("MBC-204");
    }

    @Test
    void rejectsBlankImportRequest() {
        assertThatThrownBy(() -> service.importTicket(new JiraTicketImportRequest("", "")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ticketKey or ticketUrl");
    }
}
