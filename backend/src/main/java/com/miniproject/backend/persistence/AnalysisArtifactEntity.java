package com.miniproject.backend.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Persisted form of an Artifact.v1 envelope (Artifact.java). taskId is the
 * primary key — it's already a UUID generated once per artifact, so there's
 * no separate surrogate id. The skill-specific `result` is stored as one
 * JSON blob rather than normalized per skill: three skills (code-qa,
 * impact-analysis, test-case-gen) each have a differently-shaped result, and
 * fully normalizing all three into relational columns would need a table
 * per skill for no real query benefit in this iteration — a deliberate
 * simplification, not an oversight (see docs/proposal.md Section 5.6).
 * Evidence is normalized (one row per citation) since every skill shares
 * that shape and "how many claims cite issue #108 across artifacts" is a
 * real query this makes possible.
 */
@Entity
@Table(name = "analysis_artifacts")
public class AnalysisArtifactEntity {

    @Id
    @Column(name = "task_id", length = 36)
    private String taskId;

    @Column(nullable = false)
    private String profile;

    @Column(nullable = false)
    private String agent;

    @Column(nullable = false)
    private String skill;

    @Lob
    @Column(name = "input_text", columnDefinition = "LONGTEXT")
    private String inputText;

    @Lob
    @Column(name = "result_json", nullable = false, columnDefinition = "LONGTEXT")
    private String resultJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean reviewed;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    /** Set when this artifact was created via a deterministic handoff (Section 5.7) — the source artifact's task_id. */
    @Column(name = "parent_task_id", length = 36)
    private String parentTaskId;

    /**
     * local_path of whichever workspace was active when this artifact was
     * created — lets the tracker scope tickets to the currently active
     * project instead of mixing every connected project's tickets together.
     * Nullable: artifacts created before this field existed, or with no
     * workspace connected, simply won't match any project filter.
     */
    @Column(name = "project_path")
    private String projectPath;

    @OneToMany(mappedBy = "artifact", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderColumn(name = "position")
    private List<EvidenceEntity> evidence = new ArrayList<>();

    protected AnalysisArtifactEntity() {
        // JPA
    }

    public AnalysisArtifactEntity(String taskId, String profile, String agent, String skill,
            String inputText, String resultJson, Instant createdAt, String parentTaskId, String projectPath) {
        this.taskId = taskId;
        this.profile = profile;
        this.agent = agent;
        this.skill = skill;
        this.inputText = inputText;
        this.resultJson = resultJson;
        this.createdAt = createdAt;
        this.reviewed = false;
        this.parentTaskId = parentTaskId;
        this.projectPath = projectPath;
    }

    public void addEvidence(String claim, String source) {
        EvidenceEntity e = new EvidenceEntity(this, claim, source);
        evidence.add(e);
    }

    public void markReviewed(Instant when) {
        this.reviewed = true;
        this.reviewedAt = when;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getProfile() {
        return profile;
    }

    public String getAgent() {
        return agent;
    }

    public String getSkill() {
        return skill;
    }

    public String getInputText() {
        return inputText;
    }

    public String getResultJson() {
        return resultJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isReviewed() {
        return reviewed;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public String getParentTaskId() {
        return parentTaskId;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public List<EvidenceEntity> getEvidence() {
        return evidence;
    }
}
