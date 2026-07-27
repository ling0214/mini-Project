package com.miniproject.backend.integrations;

public record ExternalHandoffRequest(
        String destination,
        String summary,
        String description,
        String prUrl,
        Boolean dryRun) {
}
