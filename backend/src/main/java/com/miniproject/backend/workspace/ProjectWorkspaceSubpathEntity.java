package com.miniproject.backend.workspace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A named folder under one project (e.g. "Frontend" -> pruserveplus-admin-console/src/main/webapp,
 * "Backend" -> pruserveplus-admin-console/src/main/java, "Admin console" -> a sibling
 * folder entirely). Exists because a single local_path can't represent a
 * project that is actually a monorepo (frontend+backend in one folder) or a
 * family of sibling folders (backend repo + separate admin-console repo) --
 * see ProjectWorkspaceEntity.graphifyIndexPath, which this generalizes from
 * "one override path" to "any number of named ones the analyst points at."
 * Deliberately a flat entity keyed by workspaceId (not a JPA @OneToMany), same
 * pattern as HermesSetupProfileEntity/HermesStatusEntity elsewhere in this codebase.
 */
@Entity
@Table(name = "project_workspace_subpaths")
public class ProjectWorkspaceSubpathEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "workspace_id", nullable = false)
    private String workspaceId;

    /** Free text, not a fixed enum -- "Frontend"/"Backend"/"Admin console"/whatever the analyst calls it. */
    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private String path;

    /**
     * The codebase-memory-mcp project name this sub-path's architecture graph
     * is indexed under -- distinct from the parent workspace's own name, e.g.
     * "pruserveplus-admin-console :: Frontend", so picking a sub-path in
     * Project Overview never overwrites or requires re-indexing the whole-
     * project graph. Null until indexSubpath() has run once.
     */
    @Column(name = "indexed_project_name")
    private String indexedProjectName;

    /** not_indexed | indexing | ready | failed -- same vocabulary as ProjectWorkspaceEntity.indexStatus. */
    @Column(name = "index_status")
    private String indexStatus;

    @Column(name = "index_error")
    private String indexError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ProjectWorkspaceSubpathEntity() {
        // JPA
    }

    public ProjectWorkspaceSubpathEntity(String id, String workspaceId, String label, String path, Instant createdAt) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.label = label;
        this.path = path;
        this.indexStatus = "not_indexed";
        this.createdAt = createdAt;
    }

    public void markIndexing(String indexedProjectName) {
        this.indexedProjectName = indexedProjectName;
        this.indexStatus = "indexing";
        this.indexError = null;
    }

    public void markIndexReady() {
        this.indexStatus = "ready";
        this.indexError = null;
    }

    public void markIndexFailed(String error) {
        this.indexStatus = "failed";
        this.indexError = error == null || error.length() <= 240 ? error : error.substring(0, 237) + "...";
    }

    public String getId() {
        return id;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public String getLabel() {
        return label;
    }

    public String getPath() {
        return path;
    }

    public String getIndexedProjectName() {
        return indexedProjectName;
    }

    public String getIndexStatus() {
        return indexStatus;
    }

    public String getIndexError() {
        return indexError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
