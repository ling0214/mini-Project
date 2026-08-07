package com.miniproject.backend.persistence;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "skill_scans")
public class SkillScanEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String skillAssetId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScanType scanType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ScanStatus status;

    @Column(columnDefinition = "LONGTEXT")
    private String findingsJson;

    @Column(name = "scanned_at", nullable = false)
    private LocalDateTime scannedAt;

    @Column(nullable = false)
    private String scannedBy;

    // Constructors
    public SkillScanEntity() {}

    public SkillScanEntity(String skillAssetId, ScanType scanType, ScanStatus status,
                          String findingsJson, String scannedBy) {
        this.skillAssetId = skillAssetId;
        this.scanType = scanType;
        this.status = status;
        this.findingsJson = findingsJson;
        this.scannedBy = scannedBy;
        this.scannedAt = LocalDateTime.now();
    }

    // Getters & Setters
    public String getId() { return id; }

    public String getSkillAssetId() { return skillAssetId; }
    public void setSkillAssetId(String skillAssetId) { this.skillAssetId = skillAssetId; }

    public ScanType getScanType() { return scanType; }
    public void setScanType(ScanType scanType) { this.scanType = scanType; }

    public ScanStatus getStatus() { return status; }
    public void setStatus(ScanStatus status) { this.status = status; }

    public String getFindingsJson() { return findingsJson; }
    public void setFindingsJson(String findingsJson) { this.findingsJson = findingsJson; }

    public LocalDateTime getScannedAt() { return scannedAt; }

    public String getScannedBy() { return scannedBy; }
    public void setScannedBy(String scannedBy) { this.scannedBy = scannedBy; }

    // Enums
    public enum ScanType {
        MECHANICAL,
        SECURITY,
        PERFORMANCE,
        INTEGRATION
    }

    public enum ScanStatus {
        PASS,
        REVIEW_NEEDED,
        FAIL
    }
}
