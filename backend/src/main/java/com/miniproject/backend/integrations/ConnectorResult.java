package com.miniproject.backend.integrations;

record ConnectorResult(
        String status,
        String externalKey,
        String externalUrl,
        String message,
        boolean dryRun) {
}
