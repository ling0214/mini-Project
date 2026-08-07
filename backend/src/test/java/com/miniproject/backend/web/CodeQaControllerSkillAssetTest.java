package com.miniproject.backend.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniproject.backend.coordinator.CoordinatorService;
import com.miniproject.backend.persistence.*;
import com.miniproject.backend.skills.SkillAssetService;
import com.miniproject.backend.skills.SkillScannerService;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class CodeQaControllerSkillAssetTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SkillAssetRepository skillAssetRepository;

    @Autowired
    private SkillApprovalRepository approvalRepository;

    @Autowired
    private SkillScanRepository scanRepository;

    @Test
    public void testSubmitForReview_EndpointWorks() throws Exception {
        SkillSubmitRequest request = new SkillSubmitRequest(
                "artifact-123",
                "0.1.0",
                "Please review this skill",
                "test-user"
        );

        mockMvc.perform(post("/api/skills/test-skill/submit-for-review")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.approval_id").exists())
                .andExpect(jsonPath("$.skill_asset_id").exists())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    public void testGetApprovals_ReturnsApprovals() throws Exception {
        // First, submit a skill
        SkillSubmitRequest request = new SkillSubmitRequest(
                "artifact-123",
                "0.1.0",
                "Review me",
                "test-user"
        );

        mockMvc.perform(post("/api/skills/test-skill/submit-for-review")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Now get approvals
        mockMvc.perform(get("/api/skills/approvals")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].submitted_by").value("test-user"));
    }

    @Test
    public void testGetApprovals_FiltersByStatus() throws Exception {
        // Submit a skill
        SkillSubmitRequest request = new SkillSubmitRequest(
                "artifact-123",
                "0.1.0",
                "Review me",
                "test-user"
        );

        mockMvc.perform(post("/api/skills/test-skill/submit-for-review")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Query for PENDING
        mockMvc.perform(get("/api/skills/approvals?status=PENDING")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").exists());
    }

    @Test
    public void testApproveSkill_EndpointWorks() throws Exception {
        // Step 1: Submit a skill
        SkillSubmitRequest submitRequest = new SkillSubmitRequest(
                "artifact-123",
                "0.1.0",
                "Review me",
                "test-user"
        );

        String submitResponse = mockMvc.perform(post("/api/skills/test-skill/submit-for-review")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String approvalId = objectMapper.readTree(submitResponse).get("approval_id").asText();

        // Step 2: Approve the skill (force=true — "test-skill" has no mapped
        // source file, so its scan always comes back REVIEW_NEEDED)
        SkillApproveRequest approveRequest = new SkillApproveRequest(
                "reviewer-user",
                "TEAM",
                "Looks good!",
                true
        );

        mockMvc.perform(post("/api/skills/approvals/" + approvalId + "/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(approveRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skill_name").value("test-skill"))
                .andExpect(jsonPath("$.status").value("PRODUCTION"))
                .andExpect(jsonPath("$.promoted").value(true))
                .andExpect(jsonPath("$.share_scope").value("TEAM"));
    }

    @Test
    public void testApproveSkill_WithoutForce_BlocksOnReviewNeededScan() throws Exception {
        // "test-skill" has no mapped source file -> scan is REVIEW_NEEDED ->
        // promotion is blocked when force is omitted.
        SkillSubmitRequest submitRequest = new SkillSubmitRequest(
                "artifact-123",
                "0.1.0",
                "Review me",
                "test-user"
        );

        String submitResponse = mockMvc.perform(post("/api/skills/test-skill/submit-for-review")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String approvalId = objectMapper.readTree(submitResponse).get("approval_id").asText();

        SkillApproveRequest approveRequest = new SkillApproveRequest(
                "reviewer-user",
                "TEAM",
                "Looks good!",
                null
        );

        mockMvc.perform(post("/api/skills/approvals/" + approvalId + "/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(approveRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.promoted").value(false))
                .andExpect(jsonPath("$.scan_status").value("REVIEW_NEEDED"));
    }

    @Test
    public void testGetManifest_ReturnsProductionSkills() throws Exception {
        // Submit and approve a skill
        SkillSubmitRequest submitRequest = new SkillSubmitRequest(
                "artifact-123",
                "0.1.0",
                "Review me",
                "test-user"
        );

        String submitResponse = mockMvc.perform(post("/api/skills/test-skill/submit-for-review")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String approvalId = objectMapper.readTree(submitResponse).get("approval_id").asText();

        SkillApproveRequest approveRequest = new SkillApproveRequest(
                "reviewer-user",
                "PUBLIC",
                "Approved",
                true
        );

        mockMvc.perform(post("/api/skills/approvals/" + approvalId + "/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(approveRequest)))
                .andExpect(status().isOk());

        // Now check manifest
        mockMvc.perform(get("/api/skills/manifest")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills[0].name").value("test-skill"))
                .andExpect(jsonPath("$.skills[0].status").value("PRODUCTION"));
    }

    @Test
    public void testGetManifest_FiltersByScope() throws Exception {
        // Submit and approve a TEAM skill
        SkillSubmitRequest submitRequest = new SkillSubmitRequest(
                "artifact-123",
                "0.1.0",
                "Review me",
                "test-user"
        );

        String submitResponse = mockMvc.perform(post("/api/skills/test-skill-team/submit-for-review")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String approvalId = objectMapper.readTree(submitResponse).get("approval_id").asText();

        SkillApproveRequest approveRequest = new SkillApproveRequest(
                "reviewer-user",
                "TEAM",
                "Approved",
                true
        );

        mockMvc.perform(post("/api/skills/approvals/" + approvalId + "/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(approveRequest)))
                .andExpect(status().isOk());

        // Query for TEAM scope only
        mockMvc.perform(get("/api/skills/manifest?scope=TEAM")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills[0].share_scope").value("TEAM"));
    }

    @Test
    public void testEndToEnd_SubmitScanApproveWorkflow() throws Exception {
        // Step 1: Submit for review
        SkillSubmitRequest submitRequest = new SkillSubmitRequest(
                "artifact-xyz",
                "0.2.0",
                "New skill feature",
                "alice"
        );

        String submitResponse = mockMvc.perform(post("/api/skills/my-skill/submit-for-review")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(submitRequest)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String approvalId = objectMapper.readTree(submitResponse).get("approval_id").asText();

        // Step 2: Verify it's in PENDING approvals
        mockMvc.perform(get("/api/skills/approvals?status=PENDING")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(approvalId));

        // Step 3: Approve the skill
        SkillApproveRequest approveRequest = new SkillApproveRequest(
                "bob",
                "PUBLIC",
                "All checks passed",
                true
        );

        mockMvc.perform(post("/api/skills/approvals/" + approvalId + "/approve")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(approveRequest)))
                .andExpect(status().isOk());

        // Step 4: Verify manifest now contains the skill with our skill name
        mockMvc.perform(get("/api/skills/manifest")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills[*].name").value(Matchers.hasItem("my-skill")));
    }
}
