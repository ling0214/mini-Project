package com.miniproject.backend.web;

public record DeclareWorkspaceRequest(String name, String repoUrl, String localPath) {
}
