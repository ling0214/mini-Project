package com.miniproject.backend.memory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One compressed "memory card" per reviewed artifact — created the moment an
 * artifact is marked reviewed (ArtifactPersistenceService.markReviewed), not
 * derived on demand from the full result_json. Modeled after the "Memory
 * Trees" idea (compress into scored, retrievable summaries rather than
 * re-scanning full records every query) rather than a vector-embedding store,
 * consistent with this codebase's existing keyword-retrieval approach
 * (ProjectContextMatcher) — no new infra dependency.
 */
@Entity
@Table(name = "memory_cards")
public class MemoryCardEntity {

    @Id
    @Column(name = "task_id", length = 36)
    private String taskId;

    @Column(nullable = false)
    private String skill;

    @Lob
    @Column(name = "summary_markdown", nullable = false)
    private String summaryMarkdown;

    @Lob
    @Column(name = "search_terms", nullable = false)
    private String searchTerms;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MemoryCardEntity() {
        // JPA
    }

    public MemoryCardEntity(String taskId, String skill, String summaryMarkdown, String searchTerms, Instant createdAt) {
        this.taskId = taskId;
        this.skill = skill;
        this.summaryMarkdown = summaryMarkdown;
        this.searchTerms = searchTerms;
        this.createdAt = createdAt;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getSkill() {
        return skill;
    }

    public String getSummaryMarkdown() {
        return summaryMarkdown;
    }

    public String getSearchTerms() {
        return searchTerms;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
