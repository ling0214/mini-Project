package com.miniproject.backend.workspace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectWorkspaceSubpathRepository extends JpaRepository<ProjectWorkspaceSubpathEntity, String> {

    List<ProjectWorkspaceSubpathEntity> findByWorkspaceIdOrderByCreatedAtAsc(String workspaceId);

    void deleteByWorkspaceId(String workspaceId);
}
