package com.miniproject.backend.web;

import com.miniproject.backend.workspace.ProjectWorkspaceSubpathEntity;

public record ProjectWorkspaceSubpathView(
        String id, String workspaceId, String label, String path,
        String indexedProjectName, String indexStatus, String indexError, String createdAt) {

    public static ProjectWorkspaceSubpathView of(ProjectWorkspaceSubpathEntity entity) {
        return new ProjectWorkspaceSubpathView(
                entity.getId(), entity.getWorkspaceId(), entity.getLabel(), entity.getPath(),
                entity.getIndexedProjectName(), entity.getIndexStatus(), entity.getIndexError(),
                entity.getCreatedAt().toString());
    }
}
