package com.miniproject.backend.integrations;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvTicketImportServiceTest {

    private final CsvTicketImportService service = new CsvTicketImportService();

    @Test
    void importsFirstDataRowMappedByHeader() {
        String csv = "Key,Title,Priority,Reporter,Description,Acceptance Criteria,Comments\n"
                + "MBC-301,Allow city filter,High,Ops Lead,Donor should filter by city,Given/when/then,Confirm with QA";

        JiraTicketImportResponse response = service.importTicket(new CsvTicketImportRequest(csv));

        assertThat(response.ticketKey()).isEqualTo("MBC-301");
        assertThat(response.ticketTitle()).isEqualTo("Allow city filter");
        assertThat(response.priority()).isEqualTo("High");
        assertThat(response.reporter()).isEqualTo("Ops Lead");
        assertThat(response.description()).isEqualTo("Donor should filter by city");
        assertThat(response.acceptanceCriteria()).isEqualTo("Given/when/then");
        assertThat(response.comments()).isEqualTo("Confirm with QA");
        assertThat(response.source()).isEqualTo("csv");
        assertThat(response.sourceType()).isEqualTo("CSV");
        assertThat(response.dryRun()).isFalse();
    }

    @Test
    void handlesQuotedFieldsWithEmbeddedCommasAndNewlines() {
        String csv = "title,description\n"
                + "\"Filter, sort, and export\",\"Line one.\nLine two, with a comma.\"";

        JiraTicketImportResponse response = service.importTicket(new CsvTicketImportRequest(csv));

        assertThat(response.ticketTitle()).isEqualTo("Filter, sort, and export");
        assertThat(response.description()).isEqualTo("Line one.\nLine two, with a comma.");
    }

    @Test
    void acceptsAlternateHeaderNames() {
        String csv = "id,summary,assignee,desc\nMBC-9,Notify admins,QA Lead,Send a notification";

        JiraTicketImportResponse response = service.importTicket(new CsvTicketImportRequest(csv));

        assertThat(response.ticketKey()).isEqualTo("MBC-9");
        assertThat(response.ticketTitle()).isEqualTo("Notify admins");
        assertThat(response.reporter()).isEqualTo("QA Lead");
        assertThat(response.description()).isEqualTo("Send a notification");
    }

    @Test
    void defaultsPriorityAndReporterWhenColumnsAreMissing() {
        String csv = "title,description\nNo priority column,Some description text";

        JiraTicketImportResponse response = service.importTicket(new CsvTicketImportRequest(csv));

        assertThat(response.priority()).isEqualTo("Medium");
        assertThat(response.reporter()).isEqualTo("Spreadsheet import");
    }

    @Test
    void rejectsBlankCsv() {
        assertThatThrownBy(() -> service.importTicket(new CsvTicketImportRequest("  ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("csvText is required");
    }

    @Test
    void rejectsCsvWithNoDataRow() {
        assertThatThrownBy(() -> service.importTicket(new CsvTicketImportRequest("title,description")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("header row and at least one data row");
    }

    @Test
    void rejectsCsvWithNoRecognizedHeaders() {
        assertThatThrownBy(() -> service.importTicket(new CsvTicketImportRequest("foo,bar\n1,2")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No recognized column headers");
    }
}
