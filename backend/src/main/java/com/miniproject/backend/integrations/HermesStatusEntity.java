package com.miniproject.backend.integrations;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per Hermes progress report for a handed-off task. A new status
 * report supersedes the previous "current" row for the same source_task_id
 * (delete_date gets stamped on the old row) rather than overwriting it, so
 * the full history stays queryable for the tracker timeline while
 * "current status" is always the single row with delete_date IS NULL.
 *
 * project identifies which declared mini-Project workspace this task belongs
 * to (e.g. "PSP"), so the tracker only shows tasks relevant to the currently
 * active project instead of mixing every connected project's Hermes traffic
 * together. It's set once (at handoff time by mini-Project, or by Hermes
 * itself for email-originated incidents) and carried forward on supersede
 * when a later status report doesn't repeat it.
 */
@Entity
@Table(name = "hermes_status")
public class HermesStatusEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "source_task_id", nullable = false, length = 36)
    private String sourceTaskId;

    @Column(nullable = false)
    private String status;

    private String note;

    private String project;

    /**
     * Raw markdown from Hermes's "similar-issue-check" RAG stage
     * (rag_similar_issue.json's raw_result) -- Hermes already runs this
     * search internally, this just surfaces it in mini-Project instead of
     * being invisible outside Hermes's own logs. Carried forward on
     * supersede the same way project is, since later status updates don't
     * repeat it.
     */
    @Lob
    @Column(name = "similar_issues", columnDefinition = "LONGTEXT")
    private String similarIssues;

    @Column(name = "create_date", nullable = false)
    private Instant createDate;

    @Column(name = "delete_date")
    private Instant deleteDate;

    protected HermesStatusEntity() {
        // JPA
    }

    public HermesStatusEntity(String sourceTaskId, String status, String note, String project, String similarIssues) {
        this.id = UUID.randomUUID().toString();
        this.sourceTaskId = sourceTaskId;
        this.status = status;
        this.note = note;
        this.project = project;
        this.similarIssues = similarIssues;
        this.createDate = Instant.now();
    }

    public void markSuperseded() {
        this.deleteDate = Instant.now();
    }

    public HermesStatusView toView() {
        return new HermesStatusView(
                id,
                sourceTaskId,
                status,
                note,
                project,
                similarIssues,
                createDate.toString(),
                deleteDate == null ? null : deleteDate.toString());
    }

    public String getSourceTaskId() {
        return sourceTaskId;
    }

    public String getProject() {
        return project;
    }

    public String getSimilarIssues() {
        return similarIssues;
    }

    public Instant getCreateDate() {
        return createDate;
    }
}
