package com.miniproject.backend.integrations;

public record HermesStatusUpdateRequest(
        String sourceTaskId, String status, String note, String project, String similarIssues) {
}
