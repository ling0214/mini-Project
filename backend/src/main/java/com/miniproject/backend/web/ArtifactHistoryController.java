package com.miniproject.backend.web;

import com.miniproject.backend.artifact.Artifact;
import com.miniproject.backend.coordinator.CoordinatorService;
import com.miniproject.backend.integrations.ExternalConnectorException;
import com.miniproject.backend.integrations.ExternalHandoffRequest;
import com.miniproject.backend.integrations.ExternalHandoffResult;
import com.miniproject.backend.integrations.ExternalHandoffService;
import com.miniproject.backend.persistence.ArtifactPersistenceService;
import com.miniproject.backend.skills.HandoffSummaryResult;
import com.miniproject.backend.skills.ImpactAnalysisResult;
import com.miniproject.backend.skills.TestCaseGenResult;
import com.miniproject.backend.skills.TestScopeReviewResult;
import com.miniproject.backend.skills.TimelineEstimationResult;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Phase 3.5 (docs/proposal.md): read/review access to persisted artifacts.
 * Every artifact is already saved by CoordinatorService the moment a skill
 * runs (ArtifactPersistenceService.save) — this controller only reads and
 * flips the review flag, it never creates artifacts itself.
 */
@RestController
@RequestMapping("/api/artifacts")
public class ArtifactHistoryController {

    private final ArtifactPersistenceService persistence;
    private final CoordinatorService coordinator;
    private final ExternalHandoffService externalHandoffService;

    public ArtifactHistoryController(
            ArtifactPersistenceService persistence,
            CoordinatorService coordinator,
            ExternalHandoffService externalHandoffService) {
        this.persistence = persistence;
        this.coordinator = coordinator;
        this.externalHandoffService = externalHandoffService;
    }

    @CrossOrigin(origins = "*")
    @GetMapping
    public List<ArtifactSummaryView> list() {
        List<ArtifactPersistenceService.ArtifactSummary> summaries = persistence.listSummaries();
        List<String> taskIds = summaries.stream().map(ArtifactPersistenceService.ArtifactSummary::taskId).toList();
        Map<String, List<ExternalHandoffResult>> handoffsByArtifact = externalHandoffService.listCreatedForArtifacts(taskIds);
        return summaries.stream()
                .map(summary -> ArtifactSummaryView.of(
                        summary,
                        latestUrlFor(handoffsByArtifact.get(summary.taskId()), "jira"),
                        latestUrlFor(handoffsByArtifact.get(summary.taskId()), "bitbucket")))
                .toList();
    }

    private static String latestUrlFor(List<ExternalHandoffResult> handoffs, String destination) {
        if (handoffs == null) {
            return null;
        }
        return handoffs.stream()
                .filter(h -> destination.equals(h.destination()))
                .map(ExternalHandoffResult::externalUrl)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    @CrossOrigin(origins = "*")
    @GetMapping("/{taskId}")
    public Artifact<Object> get(@PathVariable String taskId) {
        return persistence.findArtifact(taskId)
                .orElseThrow(() -> new NoSuchElementException("No artifact found for task_id " + taskId));
    }

    @CrossOrigin(origins = "*")
    @PatchMapping("/{taskId}/review")
    public Artifact<Object> markReviewed(@PathVariable String taskId) {
        return persistence.markReviewed(taskId)
                .orElseThrow(() -> new NoSuchElementException("No artifact found for task_id " + taskId));
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/{taskId}/handoff/test-case-gen")
    public Artifact<TestCaseGenResult> handoffToTestCaseGen(
            @PathVariable String taskId, @RequestBody HandoffTestCaseGenRequest request) {
        if (request.profile() == null || request.target() == null || request.target().isBlank()) {
            throw new IllegalArgumentException("profile and target are required");
        }
        return coordinator.handoffToTestCaseGen(taskId, request.profile(), request.target());
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/{taskId}/test-scope")
    public Artifact<TestScopeReviewResult> reviewTestScope(
            @PathVariable String taskId, @RequestBody TestScopeReviewRequest request) {
        if (request.profile() == null || request.profile().isBlank()) {
            throw new IllegalArgumentException("profile is required");
        }
        return coordinator.reviewTestScope(taskId, request.profile(), request.cases(), request.notes());
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/{taskId}/handoff/impact-analysis")
    public Artifact<ImpactAnalysisResult> handoffToImpactAnalysis(
            @PathVariable String taskId, @RequestBody HandoffImpactAnalysisRequest request) {
        if (request.profile() == null || request.profile().isBlank()) {
            throw new IllegalArgumentException("profile is required");
        }
        return coordinator.handoffToImpactAnalysis(taskId, request.profile());
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/{taskId}/handoff/timeline-estimation")
    public Artifact<TimelineEstimationResult> handoffToTimelineEstimation(
            @PathVariable String taskId, @RequestBody TimelineEstimationRequest request) {
        if (request.profile() == null || request.profile().isBlank()) {
            throw new IllegalArgumentException("profile is required");
        }
        return coordinator.handoffToTimelineEstimation(
                taskId, request.profile(), request.developers(), request.testersAvailable());
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/{taskId}/handoff/handoff-summary")
    public Artifact<HandoffSummaryResult> handoffSummary(
            @PathVariable String taskId, @RequestBody HandoffSummaryRequest request) {
        if (request.profile() == null || request.profile().isBlank()
                || request.requirementTaskId() == null || request.requirementTaskId().isBlank()) {
            throw new IllegalArgumentException("profile and requirementTaskId are required");
        }
        return coordinator.handoffSummary(taskId, request.profile(), request.requirementTaskId(), request.testTaskIds());
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/{taskId}/external-handoff")
    public ExternalHandoffResult externalHandoff(
            @PathVariable String taskId, @RequestBody ExternalHandoffRequest request) {
        return externalHandoffService.handoff(taskId, request);
    }

    @CrossOrigin(origins = "*")
    @GetMapping("/{taskId}/external-handoffs")
    public List<ExternalHandoffResult> listExternalHandoffs(@PathVariable String taskId) {
        return externalHandoffService.listForArtifact(taskId);
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/{taskId}/clarify")
    public RequirementAnalysisResponse clarify(
            @PathVariable String taskId, @RequestBody ClarifyRequirementAnalysisRequest request) {
        if (request.profile() == null || !request.hasClarification()) {
            throw new IllegalArgumentException("profile and clarification answer are required");
        }
        return RequirementAnalysisResponse.of(
                coordinator.clarifyRequirementAnalysis(taskId, request.profile(), request.clarificationText()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalArgumentException e) {
        return e.getMessage();
    }

    @ExceptionHandler(ExternalConnectorException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public String handleExternalConnectorError(ExternalConnectorException e) {
        return e.getMessage();
    }

    @ExceptionHandler(NoSuchElementException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNotFound(NoSuchElementException e) {
        return e.getMessage();
    }
}
