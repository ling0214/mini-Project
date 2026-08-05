package com.miniproject.backend.web;

import com.miniproject.backend.artifact.Artifact;
import com.miniproject.backend.coordinator.CoordinatorService;
import com.miniproject.backend.github.GitHubPrException;
import com.miniproject.backend.integrations.ExternalConnectorException;
import com.miniproject.backend.skills.CodeQaResult;
import com.miniproject.backend.skills.HermesSetupWizardResult;
import com.miniproject.backend.skills.HermesTrendingDigestResult;
import com.miniproject.backend.skills.HermesVersionAdvisorResult;
import com.miniproject.backend.skills.ImpactAnalysisResult;
import com.miniproject.backend.skills.TestCaseGenResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * CORS is wide open because this is a local-only dev workbench with no auth
 * yet. Production authentication and access control are explicitly future
 * scope; see docs/architecture.md.
 */
@RestController
@RequestMapping("/api/skills")
public class CodeQaController {

    private final CoordinatorService coordinator;

    public CodeQaController(CoordinatorService coordinator) {
        this.coordinator = coordinator;
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
}
