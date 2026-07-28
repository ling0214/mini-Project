package com.miniproject.backend.integrations;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JiraTicketImportServiceTest {

    private final JiraTicketImportService service = new JiraTicketImportService();

    @Test
    void importsMyBanjirCareSampleTicketByKey() {
        JiraTicketImportResponse response = service.importTicket(new JiraTicketImportRequest("MBC-204", null));

        assertThat(response.ticketKey()).isEqualTo("MBC-204");
        assertThat(response.ticketTitle()).contains("donors");
        assertThat(response.description()).contains("Donor should be able to filter");
        assertThat(response.dryRun()).isTrue();
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
