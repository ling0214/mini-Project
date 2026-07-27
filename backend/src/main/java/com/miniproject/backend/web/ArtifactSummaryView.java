package com.miniproject.backend.web;

import com.miniproject.backend.persistence.ArtifactPersistenceService;

public record ArtifactSummaryView(
        String taskId, String profile, String skill, String inputPreview, String createdAt,
        boolean reviewed, String reviewedAt, String parentTaskId,
        String jiraUrl, String bitbucketUrl) {

    public static ArtifactSummaryView of(
            ArtifactPersistenceService.ArtifactSummary summary, String jiraUrl, String bitbucketUrl) {
        return new ArtifactSummaryView(
                summary.taskId(), summary.profile(), summary.skill(), summary.inputPreview(), summary.createdAt(),
                summary.reviewed(), summary.reviewedAt(), summary.parentTaskId(), jiraUrl, bitbucketUrl);
    }
}
