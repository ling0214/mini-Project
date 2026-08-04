package com.miniproject.backend.integrations;

public record HermesStatusView(
        String id,
        String sourceTaskId,
        String status,
        String note,
        String project,
        String similarIssues,
        String createDate,
        String deleteDate) {
}
