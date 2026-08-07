package com.miniproject.backend.skills;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniproject.backend.persistence.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class SkillAssetService {

    private final SkillAssetRepository skillAssetRepository;
    private final SkillApprovalRepository approvalRepository;
    private final SkillScanRepository scanRepository;
    private final SkillScannerService scannerService;
    private final SkillSourceResolver sourceResolver;
    private final ObjectMapper objectMapper;

    public SkillAssetService(
            SkillAssetRepository skillAssetRepository,
            SkillApprovalRepository approvalRepository,
            SkillScanRepository scanRepository,
            SkillScannerService scannerService,
            SkillSourceResolver sourceResolver,
            ObjectMapper objectMapper) {
        this.skillAssetRepository = skillAssetRepository;
        this.approvalRepository = approvalRepository;
        this.scanRepository = scanRepository;
        this.scannerService = scannerService;
        this.sourceResolver = sourceResolver;
        this.objectMapper = objectMapper;
    }

    /**
     * Submit a skill for review. Creates a SkillAsset and a SkillApproval in PENDING state.
     */
    @Transactional
    public SkillApprovalEntity submitForReview(String skillName, String sourceArtifactId, String submittedBy) {
        // Create or retrieve SkillAsset
        SkillAssetEntity asset = new SkillAssetEntity(
                skillName,
                "0.1.0",
                "unknown",
                submittedBy,
                SkillAssetEntity.ShareScope.PRIVATE);
        if (sourceArtifactId != null) {
            asset.setSourceArtifactId(sourceArtifactId);
        }
        SkillAssetEntity savedAsset = skillAssetRepository.save(asset);

        // Create approval in PENDING state
        SkillApprovalEntity approval = new SkillApprovalEntity(savedAsset.getId(), submittedBy);
        return approvalRepository.save(approval);
    }

    /**
     * Get all PRODUCTION skills, optionally filtered by scope.
     */
    @Transactional(readOnly = true)
    public List<SkillAssetEntity> getManifest() {
        return skillAssetRepository.findByStatus(SkillAssetEntity.SkillAssetStatus.PRODUCTION);
    }

    /**
     * Get all PRODUCTION skills filtered by scope.
     */
    @Transactional(readOnly = true)
    public List<SkillAssetEntity> getManifestByScope(SkillAssetEntity.ShareScope scope) {
        return skillAssetRepository.findByStatusAndShareScope(
                SkillAssetEntity.SkillAssetStatus.PRODUCTION,
                scope);
    }

    private static final String FORCE_OVERRIDE_NOTE_PREFIX = "[Overridden despite REVIEW_NEEDED scan] ";

    /**
     * Approve a skill and promote it to PRODUCTION after scanning. Equivalent
     * to {@code promoteToProduction(..., force=false)} — a REVIEW_NEEDED scan
     * blocks promotion; see the 5-arg overload.
     */
    @Transactional
    public SkillAssetEntity promoteToProduction(String approvalId, SkillAssetEntity.ShareScope shareScope, String approverNotes, String approvedBy) {
        return promoteToProduction(approvalId, shareScope, approverNotes, approvedBy, false);
    }

    /**
     * Approve a skill and promote it to PRODUCTION after scanning.
     *
     * <p>A REVIEW_NEEDED (or FAIL) scan blocks promotion by default — the
     * scan and approval are still recorded (approval moves to REVIEW_NEEDED,
     * not APPROVED), but the asset itself stays at its current status
     * instead of reaching PRODUCTION. Pass {@code force=true} to promote
     * anyway; the override is recorded in the approval notes for audit.
     *
     * @return the asset, PRODUCTION if promotion happened, unchanged status
     *         if it was blocked — callers must check {@code getStatus()}
     *         rather than assume promotion succeeded.
     */
    @Transactional
    public SkillAssetEntity promoteToProduction(
            String approvalId, SkillAssetEntity.ShareScope shareScope, String approverNotes, String approvedBy, boolean force) {
        SkillApprovalEntity approval = approvalRepository.findById(approvalId)
                .orElseThrow(() -> new NoSuchElementException("Approval not found: " + approvalId));

        SkillAssetEntity asset = skillAssetRepository.findById(approval.getSkillAssetId())
                .orElseThrow(() -> new NoSuchElementException("Skill asset not found: " + approval.getSkillAssetId()));

        // Trigger scan before promotion — real source when we have a mapped
        // file for this skill, otherwise scanSkill flags it REVIEW_NEEDED
        // rather than silently passing on nothing to scan.
        String sourceCode = sourceResolver.resolveSource(asset.getSkillName()).orElse(null);
        SkillScannerService.SkillScanResult scanResult = scannerService.scanSkill(asset.getSkillName(), sourceCode);
        String findingsJson;
        try {
            findingsJson = objectMapper.writeValueAsString(scanResult.findings);
        } catch (JsonProcessingException e) {
            findingsJson = "[]";
        }

        boolean scanClean = scanResult.status == SkillScannerService.ScanStatus.PASS;
        SkillScanEntity scan = new SkillScanEntity(
                asset.getId(),
                SkillScanEntity.ScanType.MECHANICAL,
                scanClean ? SkillScanEntity.ScanStatus.PASS : SkillScanEntity.ScanStatus.REVIEW_NEEDED,
                findingsJson,
                approvedBy);
        scanRepository.save(scan);

        if (!scanClean && !force) {
            // Blocked: record that someone looked at it and why it's stuck,
            // but do not touch the asset — it stays wherever it already was.
            approval.setApprovalState(SkillApprovalEntity.ApprovalState.REVIEW_NEEDED);
            approval.setApprovedBy(approvedBy);
            approval.setApprovedAt(LocalDateTime.now());
            approval.setNotes(approverNotes);
            approvalRepository.save(approval);
            return asset;
        }

        // Update approval
        approval.setApprovalState(SkillApprovalEntity.ApprovalState.APPROVED);
        approval.setApprovedBy(approvedBy);
        approval.setApprovedAt(LocalDateTime.now());
        approval.setNotes(!scanClean ? FORCE_OVERRIDE_NOTE_PREFIX + (approverNotes == null ? "" : approverNotes) : approverNotes);
        approval.setShareScope(shareScope.toString());
        approvalRepository.save(approval);

        // Update asset to PRODUCTION
        asset.setStatus(SkillAssetEntity.SkillAssetStatus.PRODUCTION);
        asset.setShareScope(shareScope);
        asset.setUpdatedAt(LocalDateTime.now());
        return skillAssetRepository.save(asset);
    }

    /**
     * Get pending approvals.
     */
    @Transactional(readOnly = true)
    public List<SkillApprovalEntity> getPendingApprovals() {
        return approvalRepository.findByApprovalStateOrderBySubmittedAtDesc(SkillApprovalEntity.ApprovalState.PENDING);
    }

    /**
     * Get approvals by state.
     */
    @Transactional(readOnly = true)
    public List<SkillApprovalEntity> getApprovalsByState(SkillApprovalEntity.ApprovalState state) {
        return approvalRepository.findByApprovalStateOrderBySubmittedAtDesc(state);
    }

    /**
     * Get a specific approval by ID.
     */
    @Transactional(readOnly = true)
    public Optional<SkillApprovalEntity> getApproval(String approvalId) {
        return approvalRepository.findById(approvalId);
    }

    /**
     * Get all approvals for a specific skill asset.
     */
    @Transactional(readOnly = true)
    public List<SkillApprovalEntity> getApprovalsForAsset(String skillAssetId) {
        return approvalRepository.findBySkillAssetIdOrderBySubmittedAtDesc(skillAssetId);
    }

    /**
     * Get scan results for a skill asset.
     */
    @Transactional(readOnly = true)
    public Optional<SkillScanEntity> getLatestScan(String skillAssetId) {
        return scanRepository.findFirstBySkillAssetIdOrderByScannedAtDesc(skillAssetId);
    }

    /**
     * Get skill asset by ID.
     */
    @Transactional(readOnly = true)
    public Optional<SkillAssetEntity> getSkillAsset(String skillAssetId) {
        return skillAssetRepository.findById(skillAssetId);
    }
}
