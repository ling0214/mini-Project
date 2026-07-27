package com.miniproject.backend.integrations;

public record ExternalHandoffResult(
        String id,
        String sourceTaskId,
        String destination,
        String status,
        String externalKey,
        String externalUrl,
        String message,
        boolean dryRun,
        String createdAt) {
}
