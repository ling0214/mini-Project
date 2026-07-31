package com.miniproject.backend.web;

import com.miniproject.backend.workspace.ProjectWorkspaceEntity;

public record ProjectWorkspaceView(
        String id, String name, String repoUrl, String localPath, boolean active,
        String indexStatus, String indexError,
        String graphifyIndexStatus, String graphifyIndexError,
        String createdAt, String lastActivatedAt) {

    public static ProjectWorkspaceView of(ProjectWorkspaceEntity entity) {
        return new ProjectWorkspaceView(
                entity.getId(), entity.getName(), entity.getRepoUrl(), entity.getLocalPath(), entity.isActive(),
                entity.getIndexStatus(), entity.getIndexError(),
                entity.getGraphifyIndexStatus(), entity.getGraphifyIndexError(),
                entity.getCreatedAt().toString(), entity.getLastActivatedAt().toString());
    }
}
