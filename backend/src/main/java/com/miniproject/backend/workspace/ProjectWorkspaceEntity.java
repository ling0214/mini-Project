package com.miniproject.backend.workspace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A project the analyst has declared (name + local repo path, optional repo
 * URL for reference only — this does not clone anything). Exactly one row
 * has active=true at a time; ProjectWorkspaceService keeps that invariant and
 * pushes the active name/path into ProjectContextMatcher so impact-analysis
 * uses whichever project the analyst last selected instead of a fixed
 * server-config default.
 */
@Entity
@Table(name = "project_workspaces")
public class ProjectWorkspaceEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "repo_url")
    private String repoUrl;

    @Column(name = "local_path", nullable = false)
    private String localPath;

    @Column(nullable = false)
    private boolean active;

    /**
     * not_indexed | indexing | ready | failed — see ProjectWorkspaceService.declare/indexAsync.
     * Nullable (despite always being set by code) so adding this column to an
     * already-populated project_workspaces table doesn't need a backfill
     * default — H2/Hibernate auto-DDL refuses "ADD COLUMN ... NOT NULL" once
     * rows already exist. Old rows just read back as null, which every null
     * check here and on the frontend already treats as "not indexed".
     */
    @Column(name = "index_status")
    private String indexStatus;

    @Column(name = "index_error")
    private String indexError;

    @Column(name = "graphify_index_status")
    private String graphifyIndexStatus;

    @Column(name = "graphify_index_error")
    private String graphifyIndexError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_activated_at", nullable = false)
    private Instant lastActivatedAt;

    protected ProjectWorkspaceEntity() {
        // JPA
    }

    public ProjectWorkspaceEntity(String id, String name, String repoUrl, String localPath, Instant now) {
        this.id = id;
        this.name = name;
        this.repoUrl = repoUrl;
        this.localPath = localPath;
        this.active = true;
        this.indexStatus = "not_indexed";
        this.graphifyIndexStatus = "not_indexed";
        this.createdAt = now;
        this.lastActivatedAt = now;
    }

    public void markIndexing() {
        this.indexStatus = "indexing";
        this.indexError = null;
    }

    public void markIndexReady() {
        this.indexStatus = "ready";
        this.indexError = null;
    }

    public void markIndexFailed(String error) {
        this.indexStatus = "failed";
        this.indexError = truncateError(error);
    }

    public void markGraphifyIndexing() {
        this.graphifyIndexStatus = "indexing";
        this.graphifyIndexError = null;
    }

    public void markGraphifyIndexReady() {
        this.graphifyIndexStatus = "ready";
        this.graphifyIndexError = null;
    }

    public void markGraphifyIndexFailed(String error) {
        this.graphifyIndexStatus = "failed";
        this.graphifyIndexError = truncateError(error);
    }

    public void deactivate() {
        this.active = false;
    }

    public void activate(Instant when) {
        this.active = true;
        this.lastActivatedAt = when;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getRepoUrl() {
        return repoUrl;
    }

    public String getLocalPath() {
        return localPath;
    }

    public boolean isActive() {
        return active;
    }

    public String getIndexStatus() {
        return indexStatus;
    }

    public String getIndexError() {
        return indexError;
    }

    public String getGraphifyIndexStatus() {
        return graphifyIndexStatus;
    }

    public String getGraphifyIndexError() {
        return graphifyIndexError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getLastActivatedAt() {
        return lastActivatedAt;
    }

    private static String truncateError(String error) {
        if (error == null || error.length() <= 240) {
            return error;
        }
        return error.substring(0, 237) + "...";
    }
}
