package com.miniproject.backend.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "skill_approvals")
public class SkillApprovalEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String skillAssetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalState approvalState;

    @Column(nullable = false)
    private String submittedBy;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "approved_by")
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(nullable = false, length = 20)
    private String shareScope; // PRIVATE, TEAM, PUBLIC

    // Constructors
    public SkillApprovalEntity() {}

    public SkillApprovalEntity(String skillAssetId, String submittedBy) {
        this.skillAssetId = skillAssetId;
        this.approvalState = ApprovalState.PENDING;
        this.submittedBy = submittedBy;
        this.submittedAt = LocalDateTime.now();
        this.shareScope = "PRIVATE";
    }

    // Getters & Setters
    public String getId() { return id; }

    public String getSkillAssetId() { return skillAssetId; }
    public void setSkillAssetId(String skillAssetId) { this.skillAssetId = skillAssetId; }

    public ApprovalState getApprovalState() { return approvalState; }
    public void setApprovalState(ApprovalState approvalState) { this.approvalState = approvalState; }

    public String getSubmittedBy() { return submittedBy; }
    public void setSubmittedBy(String submittedBy) { this.submittedBy = submittedBy; }

    public LocalDateTime getSubmittedAt() { return submittedAt; }

    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }

    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getShareScope() { return shareScope; }
    public void setShareScope(String shareScope) { this.shareScope = shareScope; }

    // Enums
    public enum ApprovalState {
        PENDING,
        REVIEW_NEEDED,
        APPROVED,
        REJECTED
    }
}
