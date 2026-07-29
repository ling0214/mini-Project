package com.miniproject.backend.web;

public record RequirementAnalysisRequest(
        String profile,
        String description,
        String ticketKey,
        String ticketTitle,
        String priority,
        String reporter,
        String sourceType,
        String sourceName,
        String sourceUrl,
        String receivedAt,
        String acceptanceCriteria,
        String comments) {

    public String analysisInput() {
        String freeTextDescription = safe(description);
        if (safe(ticketKey).isBlank()
                && safe(ticketTitle).isBlank()
                && safe(priority).isBlank()
                && safe(reporter).isBlank()
                && safe(sourceType).isBlank()
                && safe(sourceName).isBlank()
                && safe(sourceUrl).isBlank()
                && safe(receivedAt).isBlank()
                && safe(acceptanceCriteria).isBlank()
                && safe(comments).isBlank()) {
            return freeTextDescription;
        }

        StringBuilder builder = new StringBuilder();
        appendLine(builder, "Ticket key", ticketKey);
        appendLine(builder, "Title", ticketTitle);
        appendLine(builder, "Priority", priority);
        appendLine(builder, "Reporter", reporter);
        appendLine(builder, "Source type", sourceType);
        appendLine(builder, "Source name", sourceName);
        appendLine(builder, "Source URL", sourceUrl);
        appendLine(builder, "Received", receivedAt);
        appendBlock(builder, "Description", freeTextDescription);
        appendBlock(builder, "Acceptance criteria", acceptanceCriteria);
        appendBlock(builder, "Comments / notes", comments);
        return builder.toString().trim();
    }

    private static void appendLine(StringBuilder builder, String label, String value) {
        String cleaned = safe(value);
        if (!cleaned.isBlank()) {
            builder.append(label).append(": ").append(cleaned).append('\n');
        }
    }

    private static void appendBlock(StringBuilder builder, String label, String value) {
        String cleaned = safe(value);
        if (!cleaned.isBlank()) {
            if (!builder.isEmpty()) {
                builder.append('\n');
            }
            builder.append(label).append(":\n").append(cleaned).append('\n');
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
