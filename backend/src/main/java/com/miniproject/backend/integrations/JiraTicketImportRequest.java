package com.miniproject.backend.integrations;

public record JiraTicketImportRequest(String ticketKey, String ticketUrl) {
}
