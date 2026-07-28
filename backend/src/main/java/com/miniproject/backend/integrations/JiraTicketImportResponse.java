package com.miniproject.backend.integrations;

public record JiraTicketImportResponse(
        String ticketKey,
        String ticketTitle,
        String priority,
        String reporter,
        String description,
        String acceptanceCriteria,
        String comments,
        String source,
        boolean dryRun,
        String message) {
}
