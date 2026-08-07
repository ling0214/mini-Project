package com.miniproject.backend.skills;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniproject.backend.persistence.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class SkillAssetServiceTest {

    @Autowired
    private SkillAssetService skillAssetService;

    @Autowired
    private SkillAssetRepository skillAssetRepository;

    @Autowired
    private SkillApprovalRepository approvalRepository;

    @Autowired
    private SkillScanRepository scanRepository;

    @BeforeEach
    public void setUp() {
        skillAssetRepository.deleteAll();
        approvalRepository.deleteAll();
        scanRepository.deleteAll();
    }

    @Test
    public void testSubmitForReview_CreatesApprovalInPendingState() {
        String skillName = "test-skill";
        String submittedBy = "analyst-user";

        SkillApprovalEntity approval = skillAssetService.submitForReview(skillName, null, submittedBy);

        assertNotNull(approval.getId());
        assertEquals(SkillApprovalEntity.ApprovalState.PENDING, approval.getApprovalState());
        assertEquals(submittedBy, approval.getSubmittedBy());
        assertNotNull(approval.getSubmittedAt());
    }

    @Test
    public void testSubmitForReview_CreatesSkillAsset() {
        String skillName = "test-skill";
        String sourceArtifactId = "artifact-123";
        String submittedBy = "analyst-user";

        SkillApprovalEntity approval = skillAssetService.submitForReview(skillName, sourceArtifactId, submittedBy);

        assertTrue(skillAssetRepository.existsById(approval.getSkillAssetId()));
        SkillAssetEntity asset = skillAssetRepository.findById(approval.getSkillAssetId()).get();
        assertEquals(skillName, asset.getSkillName());
        assertEquals(sourceArtifactId, asset.getSourceArtifactId());
        assertEquals(SkillAssetEntity.SkillAssetStatus.APPROVED, asset.getStatus());
    }

    @Test
    public void testGetManifest_ReturnsOnlyProductionSkills() {
        // Create one APPROVED and one PRODUCTION skill
        SkillAssetEntity approved = new SkillAssetEntity("skill-1", "0.1.0", "abc123", "user1", SkillAssetEntity.ShareScope.PRIVATE);
        approved.setStatus(SkillAssetEntity.SkillAssetStatus.APPROVED);
        skillAssetRepository.save(approved);

        SkillAssetEntity production = new SkillAssetEntity("skill-2", "1.0.0", "def456", "user2", SkillAssetEntity.ShareScope.TEAM);
        production.setStatus(SkillAssetEntity.SkillAssetStatus.PRODUCTION);
        skillAssetRepository.save(production);

        var manifest = skillAssetService.getManifest();

        assertEquals(1, manifest.size());
        assertEquals("skill-2", manifest.get(0).getSkillName());
    }

    @Test
    public void testGetManifestByScope_FiltersCorrectly() {
        SkillAssetEntity privateSkill = new SkillAssetEntity("skill-1", "0.1.0", "abc123", "user1", SkillAssetEntity.ShareScope.PRIVATE);
        privateSkill.setStatus(SkillAssetEntity.SkillAssetStatus.PRODUCTION);
        skillAssetRepository.save(privateSkill);

        SkillAssetEntity teamSkill = new SkillAssetEntity("skill-2", "1.0.0", "def456", "user2", SkillAssetEntity.ShareScope.TEAM);
        teamSkill.setStatus(SkillAssetEntity.SkillAssetStatus.PRODUCTION);
        skillAssetRepository.save(teamSkill);

        var teamManifest = skillAssetService.getManifestByScope(SkillAssetEntity.ShareScope.TEAM);

        assertEquals(1, teamManifest.size());
        assertEquals("skill-2", teamManifest.get(0).getSkillName());
    }

    @Test
    public void testPromoteToProduction_WithForce_UpdatesStatusAndCreatesApproval() {
        // "test-skill" has no mapped source file, so its scan is always
        // REVIEW_NEEDED — force=true is required to reach PRODUCTION here.
        String skillName = "test-skill";
        String submittedBy = "analyst-user";
        String approvedBy = "reviewer-user";

        // Step 1: Submit for review
        SkillApprovalEntity approval = skillAssetService.submitForReview(skillName, null, submittedBy);
        String approvalId = approval.getId();

        // Step 2: Promote to production
        SkillAssetEntity promoted = skillAssetService.promoteToProduction(
                approvalId, SkillAssetEntity.ShareScope.TEAM, "Looks good", approvedBy, true);

        assertEquals(SkillAssetEntity.SkillAssetStatus.PRODUCTION, promoted.getStatus());
        assertEquals(SkillAssetEntity.ShareScope.TEAM, promoted.getShareScope());

        // Verify approval is updated
        SkillApprovalEntity updatedApproval = approvalRepository.findById(approvalId).get();
        assertEquals(SkillApprovalEntity.ApprovalState.APPROVED, updatedApproval.getApprovalState());
        assertEquals(approvedBy, updatedApproval.getApprovedBy());
    }

    @Test
    public void testPromoteToProduction_WithoutForce_BlocksOnReviewNeededScan() {
        // "test-skill" has no mapped source file -> scan comes back
        // REVIEW_NEEDED -> promotion is blocked unless force=true.
        String skillName = "test-skill";
        SkillApprovalEntity approval = skillAssetService.submitForReview(skillName, null, "analyst-user");
        String approvalId = approval.getId();

        SkillAssetEntity result = skillAssetService.promoteToProduction(
                approvalId, SkillAssetEntity.ShareScope.TEAM, "Looks good", "reviewer-user");

        assertEquals(SkillAssetEntity.SkillAssetStatus.APPROVED, result.getStatus());
        assertNotEquals(SkillAssetEntity.SkillAssetStatus.PRODUCTION, result.getStatus());

        SkillApprovalEntity updatedApproval = approvalRepository.findById(approvalId).get();
        assertEquals(SkillApprovalEntity.ApprovalState.REVIEW_NEEDED, updatedApproval.getApprovalState());
    }

    @Test
    public void testPromoteToProduction_CreatesScanEntity() {
        String skillName = "test-skill";
        SkillApprovalEntity approval = skillAssetService.submitForReview(skillName, null, "user1");

        skillAssetService.promoteToProduction(
                approval.getId(), SkillAssetEntity.ShareScope.PRIVATE, "OK", "user2");

        var scans = scanRepository.findBySkillAssetIdOrderByScannedAtDesc(approval.getSkillAssetId());
        assertEquals(1, scans.size());
        assertNotNull(scans.get(0).getFindingsJson());
    }

    @Test
    public void testGetPendingApprovals_ReturnsPendingOnly() {
        SkillApprovalEntity approval1 = skillAssetService.submitForReview("skill-1", null, "user1");
        SkillApprovalEntity approval2 = skillAssetService.submitForReview("skill-2", null, "user1");

        // Mark approval1 as approved
        skillAssetService.promoteToProduction(approval1.getId(), SkillAssetEntity.ShareScope.PRIVATE, "OK", "reviewer");

        var pending = skillAssetService.getPendingApprovals();

        assertEquals(1, pending.size());
        assertEquals(approval2.getId(), pending.get(0).getId());
    }

    @Test
    public void testGetLatestScan_ReturnsLatestScanForAsset() {
        SkillApprovalEntity approval = skillAssetService.submitForReview("skill-1", null, "user1");
        skillAssetService.promoteToProduction(approval.getId(), SkillAssetEntity.ShareScope.PRIVATE, "OK", "reviewer");

        var latestScan = skillAssetService.getLatestScan(approval.getSkillAssetId());

        assertTrue(latestScan.isPresent());
        assertEquals(SkillScanEntity.ScanType.MECHANICAL, latestScan.get().getScanType());
    }

    @Test
    public void testApprovalStateTransitions() {
        // PENDING -> APPROVED flow (force=true since "skill-1" has no mapped
        // source and would otherwise land on REVIEW_NEEDED, not APPROVED)
        SkillApprovalEntity approval = skillAssetService.submitForReview("skill-1", null, "user1");
        assertEquals(SkillApprovalEntity.ApprovalState.PENDING, approval.getApprovalState());

        skillAssetService.promoteToProduction(approval.getId(), SkillAssetEntity.ShareScope.PUBLIC, "Great", "reviewer", true);

        SkillApprovalEntity updated = approvalRepository.findById(approval.getId()).get();
        assertEquals(SkillApprovalEntity.ApprovalState.APPROVED, updated.getApprovalState());
    }
}
