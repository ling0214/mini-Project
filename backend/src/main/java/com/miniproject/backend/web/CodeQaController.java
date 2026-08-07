package com.miniproject.backend.web;

import com.miniproject.backend.artifact.Artifact;
import com.miniproject.backend.coordinator.CoordinatorService;
import com.miniproject.backend.github.GitHubPrException;
import com.miniproject.backend.integrations.ExternalConnectorException;
import com.miniproject.backend.integrations.GitLogReader;
import com.miniproject.backend.persistence.SkillApprovalEntity;
import com.miniproject.backend.persistence.SkillAssetEntity;
import com.miniproject.backend.skills.CodeQaResult;
import com.miniproject.backend.skills.HermesSetupWizardResult;
import com.miniproject.backend.skills.HermesTrendingDigestResult;
import com.miniproject.backend.skills.HermesVersionAdvisorResult;
import com.miniproject.backend.skills.ImpactAnalysisResult;
import com.miniproject.backend.skills.SkillAssetService;
import com.miniproject.backend.skills.TestCaseGenResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CORS is wide open because this is a local-only dev workbench with no auth
 * yet. Production authentication and access control are explicitly future
 * scope; see docs/architecture.md.
 */
@RestController
@RequestMapping("/api/skills")
public class CodeQaController {

    private final CoordinatorService coordinator;
    private final GitLogReader gitLogReader;
    private final SkillAssetService skillAssetService;

    public CodeQaController(CoordinatorService coordinator, GitLogReader gitLogReader, SkillAssetService skillAssetService) {
        this.coordinator = coordinator;
        this.gitLogReader = gitLogReader;
        this.skillAssetService = skillAssetService;
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/code-qa")
    public Artifact<CodeQaResult> codeQa(@RequestBody CodeQaRequest request) {
        if (request.profile() == null || request.question() == null || request.question().isBlank()) {
            throw new IllegalArgumentException("profile and question are required");
        }
        return coordinator.codeQa(request.profile(), request.question());
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/impact-analysis")
    public Artifact<ImpactAnalysisResult> impactAnalysis(@RequestBody ImpactAnalysisRequest request) {
        if (request.profile() == null || request.changeRequest() == null || request.changeRequest().isBlank()) {
            throw new IllegalArgumentException("profile and changeRequest are required");
        }
        return coordinator.impactAnalysis(request.profile(), request.changeRequest());
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/impact-analysis/from-pr")
    public Artifact<ImpactAnalysisResult> impactAnalysisFromPr(@RequestBody PrImpactAnalysisRequest request) {
        if (request.profile() == null || request.prUrl() == null || request.prUrl().isBlank()) {
            throw new IllegalArgumentException("profile and prUrl are required");
        }
        return coordinator.impactAnalysisFromPr(request.profile(), request.prUrl());
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/test-case-gen")
    public Artifact<TestCaseGenResult> testCaseGen(@RequestBody TestCaseGenRequest request) {
        if (request.profile() == null || request.target() == null || request.target().isBlank()) {
            throw new IllegalArgumentException("profile and target are required");
        }
        return coordinator.testCaseGen(request.profile(), request.target());
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/requirement-analysis")
    public RequirementAnalysisResponse requirementAnalysis(@RequestBody RequirementAnalysisRequest request) {
        if (request.profile() == null || request.analysisInput().isBlank()) {
            throw new IllegalArgumentException("profile and ticket details are required");
        }
        return RequirementAnalysisResponse.of(coordinator.requirementAnalysis(request.profile(), request.analysisInput()));
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/hermes-setup-wizard")
    public Artifact<HermesSetupWizardResult> hermesSetupWizard(@RequestBody HermesSetupWizardRequest request) {
        if (request.profile() == null || request.repoPath() == null || request.repoPath().isBlank()) {
            throw new IllegalArgumentException("profile and repoPath are required");
        }
        return coordinator.hermesSetupWizard(request.profile(), request.toAnswers());
    }

    /**
     * Cheap, local-only git lookup (no fetch) so the wizard can show which
     * repo a path resolves to before the analyst runs a real upstream check
     * -- lets them visually confirm they typed the right folder instead of
     * only finding out from a "cannot change to ..." failure downstream.
     */
    @CrossOrigin(origins = "*")
    @GetMapping("/hermes-version-advisor/remote-info")
    public Map<String, Object> hermesVersionAdvisorRemoteInfo(@RequestParam("repo_path") String repoPath) {
        if (repoPath == null || repoPath.isBlank()) {
            throw new IllegalArgumentException("repo_path is required");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            String remoteUrl = gitLogReader.getRemoteUrl(repoPath, null);
            String webUrl = GitLogReader.toWebUrl(remoteUrl);
            body.put("is_git_repo", true);
            body.put("remote_url", remoteUrl);
            body.put("web_url", webUrl == null ? "" : webUrl);
        } catch (GitLogReader.GitLogReaderException e) {
            body.put("is_git_repo", false);
            body.put("error", e.getMessage());
        }
        return body;
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/hermes-version-advisor")
    public Artifact<HermesVersionAdvisorResult> hermesVersionAdvisor(@RequestBody HermesVersionAdvisorRequest request) {
        if (request.profile() == null || request.repoPath() == null || request.repoPath().isBlank()) {
            throw new IllegalArgumentException("profile and repoPath are required");
        }
        return coordinator.hermesVersionAdvisor(
                request.profile(), request.repoPath(), request.remoteRef(), request.localRef(), request.watchedPaths());
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/hermes-trending-digest")
    public Artifact<HermesTrendingDigestResult> hermesTrendingDigest(@RequestBody HermesTrendingDigestRequest request) {
        if (request.profile() == null || request.profile().isBlank()) {
            throw new IllegalArgumentException("profile is required");
        }
        return coordinator.hermesTrendingDigest(request.profile());
    }

    // Skill Asset Vetting Pipeline endpoints
    @CrossOrigin(origins = "*")
    @PostMapping("/{skillName}/submit-for-review")
    public Map<String, Object> submitForReview(
            @PathVariable String skillName,
            @RequestBody SkillSubmitRequest request) {
        if (skillName == null || skillName.isBlank()) {
            throw new IllegalArgumentException("skillName is required");
        }
        SkillApprovalEntity approval = skillAssetService.submitForReview(
                skillName, request.sourceArtifactId(), request.submittedBy());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("approval_id", approval.getId());
        response.put("skill_asset_id", approval.getSkillAssetId());
        response.put("status", approval.getApprovalState().toString());
        response.put("submitted_at", approval.getSubmittedAt().toString());
        return response;
    }

    @CrossOrigin(origins = "*")
    @GetMapping("/approvals")
    public List<Map<String, Object>> getApprovals(@RequestParam(required = false) String status) {
        List<SkillApprovalEntity> approvals;
        if (status != null && !status.isBlank()) {
            try {
                SkillApprovalEntity.ApprovalState state = SkillApprovalEntity.ApprovalState.valueOf(status.toUpperCase());
                approvals = skillAssetService.getApprovalsByState(state);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid status: " + status);
            }
        } else {
            approvals = skillAssetService.getPendingApprovals();
        }

        return approvals.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("skill_asset_id", a.getSkillAssetId());
            m.put("status", a.getApprovalState().toString());
            m.put("submitted_by", a.getSubmittedBy());
            m.put("submitted_at", a.getSubmittedAt().toString());
            return m;
        }).toList();
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/approvals/{approvalId}/approve")
    public Map<String, Object> approveSkill(
            @PathVariable String approvalId,
            @RequestBody SkillApproveRequest request) {
        if (approvalId == null || approvalId.isBlank()) {
            throw new IllegalArgumentException("approvalId is required");
        }
        if (request.approvedBy() == null || request.approvedBy().isBlank()) {
            throw new IllegalArgumentException("approvedBy is required");
        }

        try {
            SkillAssetEntity.ShareScope scope = SkillAssetEntity.ShareScope.valueOf(
                    request.shareScope() != null ? request.shareScope().toUpperCase() : "PRIVATE");
            SkillAssetEntity result = skillAssetService.promoteToProduction(
                    approvalId, scope, request.approverNotes(), request.approvedBy(), request.forceOrDefault());
            boolean promoted = result.getStatus() == SkillAssetEntity.SkillAssetStatus.PRODUCTION;

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("skill_asset_id", result.getId());
            response.put("skill_name", result.getSkillName());
            response.put("status", result.getStatus().toString());
            response.put("promoted", promoted);
            response.put("share_scope", result.getShareScope().toString());
            response.put("promoted_at", result.getUpdatedAt().toString());
            if (!promoted) {
                skillAssetService.getLatestScan(result.getId()).ifPresent(scan -> {
                    response.put("blocked_reason", "Scan flagged REVIEW_NEEDED and force was not set");
                    response.put("scan_status", scan.getStatus().toString());
                    response.put("scan_findings", scan.getFindingsJson());
                });
            }
            return response;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid shareScope: " + request.shareScope());
        }
    }

    @CrossOrigin(origins = "*")
    @GetMapping("/manifest")
    public Map<String, Object> getManifest(@RequestParam(required = false) String scope) {
        List<SkillAssetEntity> skills;
        if (scope != null && !scope.isBlank()) {
            try {
                SkillAssetEntity.ShareScope shareScope = SkillAssetEntity.ShareScope.valueOf(scope.toUpperCase());
                skills = skillAssetService.getManifestByScope(shareScope);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid scope: " + scope);
            }
        } else {
            skills = skillAssetService.getManifest();
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("skills", skills.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("name", s.getSkillName());
            m.put("version", s.getVersion());
            m.put("status", s.getStatus().toString());
            m.put("share_scope", s.getShareScope().toString());
            m.put("created_by", s.getCreatedBy());
            m.put("created_at", s.getCreatedAt().toString());
            return m;
        }).toList());
        response.put("count", skills.size());
        return response;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalArgumentException e) {
        return e.getMessage();
    }

    @ExceptionHandler(GitHubPrException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public String handleGitHubError(GitHubPrException e) {
        return e.getMessage();
    }

    @ExceptionHandler(ExternalConnectorException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public String handleExternalConnectorError(ExternalConnectorException e) {
        return e.getMessage();
    }

    @ExceptionHandler(GitLogReader.GitLogReaderException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public String handleGitLogReaderError(GitLogReader.GitLogReaderException e) {
        return e.getMessage();
    }
}
