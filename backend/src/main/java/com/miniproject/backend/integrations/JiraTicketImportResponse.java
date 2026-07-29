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
        String sourceType,
        String sourceName,
        String sourceUrl,
        String receivedAt,
        boolean dryRun,
        String message) {
}
