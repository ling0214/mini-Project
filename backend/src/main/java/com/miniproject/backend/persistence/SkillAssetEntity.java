package com.miniproject.backend.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "skill_assets")
public class SkillAssetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String skillName;

    @Column(nullable = false)
    private String version;

    @Column(nullable = false, length = 40)
    private String commitHash;

    private String sourceArtifactId;

    @Column(nullable = false)
    private String createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SkillAssetStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ShareScope shareScope;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // Constructors
    public SkillAssetEntity() {}

    public SkillAssetEntity(String skillName, String version, String commitHash,
                           String createdBy, ShareScope scope) {
        this.skillName = skillName;
        this.version = version;
        this.commitHash = commitHash;
        this.createdBy = createdBy;
        this.shareScope = scope;
        this.status = SkillAssetStatus.APPROVED;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getters & Setters
    public String getId() { return id; }
    public String getSkillName() { return skillName; }
    public void setSkillName(String skillName) { this.skillName = skillName; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getCommitHash() { return commitHash; }
    public void setCommitHash(String commitHash) { this.commitHash = commitHash; }

    public String getSourceArtifactId() { return sourceArtifactId; }
    public void setSourceArtifactId(String sourceArtifactId) { this.sourceArtifactId = sourceArtifactId; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public SkillAssetStatus getStatus() { return status; }
    public void setStatus(SkillAssetStatus status) { this.status = status; }

    public ShareScope getShareScope() { return shareScope; }
    public void setShareScope(ShareScope shareScope) { this.shareScope = shareScope; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    // Enums
    public enum SkillAssetStatus {
        APPROVED,
        PRODUCTION,
        RETIRED
    }

    public enum ShareScope {
        PRIVATE,
        TEAM,
        PUBLIC
    }
}
