package com.miniproject.backend.integrations;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniproject.backend.artifact.Artifact;
import com.miniproject.backend.artifact.Evidence;
import com.miniproject.backend.persistence.ArtifactPersistenceService;
import com.miniproject.backend.workspace.ProjectWorkspaceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ExternalHandoffService {

    private final ArtifactPersistenceService persistence;
    private final ExternalHandoffRepository handoffRepository;
    private final JiraConnector jiraConnector;
    private final BitbucketConnector bitbucketConnector;
    private final HermesConnector hermesConnector;
    private final HermesStatusService hermesStatusService;
    private final ProjectWorkspaceService projectWorkspaceService;
    private final ObjectMapper objectMapper;

    public ExternalHandoffService(
            ArtifactPersistenceService persistence,
            ExternalHandoffRepository handoffRepository,
            JiraConnector jiraConnector,
            BitbucketConnector bitbucketConnector,
            HermesConnector hermesConnector,
            HermesStatusService hermesStatusService,
            ProjectWorkspaceService projectWorkspaceService,
            ObjectMapper objectMapper) {
        this.persistence = persistence;
        this.handoffRepository = handoffRepository;
        this.jiraConnector = jiraConnector;
        this.bitbucketConnector = bitbucketConnector;
        this.hermesConnector = hermesConnector;
        this.hermesStatusService = hermesStatusService;
        this.projectWorkspaceService = projectWorkspaceService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ExternalHandoffResult handoff(String sourceTaskId, ExternalHandoffRequest request) {
        Artifact<Object> source = persistence.findArtifact(sourceTaskId)
                .orElseThrow(() -> new IllegalArgumentException("No artifact found for task_id " + sourceTaskId));
        if (!source.reviewed()) {
            throw new IllegalArgumentException(
                    "Artifact " + sourceTaskId + " must be reviewed before external Jira/Bitbucket handoff");
        }

        String destination = requireDestination(request.destination());
        String summary = defaultIfBlank(request.summary(), defaultSummary(source));
        String description = defaultIfBlank(request.description(), defaultDescription(source));
        boolean dryRun = request.dryRun() == null || request.dryRun();

        ConnectorResult connectorResult = switch (destination) {
            case "jira" -> jiraConnector.createIssue(summary, description, dryRun);
            case "jira-comment" -> jiraConnector.commentOnIssue(request.jiraIssueKey(), description, dryRun);
            case "bitbucket" -> bitbucketConnector.commentOnPr(request.prUrl(), description, dryRun);
            case "hermes" -> hermesConnector.sendTask(summary, description, dryRun);
            default -> throw new IllegalArgumentException("Unsupported external handoff destination: " + destination);
        };

        if ("hermes".equals(destination) && "SENT".equals(connectorResult.status())) {
            // Tags this task with the project that was active when it was sent,
            // so the tracker can later scope Hermes status to the right
            // connected project instead of mixing every project's Hermes
            // traffic together. Uses local_path, not the display name --
            // names are free text the analyst can rename anytime, while path
            // is the stable value Hermes can independently agree on too.
            // Best-effort: a workspace lookup failure here must not fail the
            // handoff itself.
            String activeProjectPath = projectWorkspaceService.current().map(w -> w.getLocalPath()).orElse(null);
            hermesStatusService.recordStatus(
                    sourceTaskId, "Sent to Hermes", "Reviewed handoff package sent to Hermes intake.", activeProjectPath, null);
        }

        ExternalHandoffEntity saved = handoffRepository.save(
                new ExternalHandoffEntity(sourceTaskId, destination, connectorResult));
        return saved.toResult();
    }

    @Transactional(readOnly = true)
    public List<ExternalHandoffResult> listForArtifact(String sourceTaskId) {
        return handoffRepository.findBySourceTaskIdOrderByCreatedAtDesc(sourceTaskId).stream()
                .map(ExternalHandoffEntity::toResult)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, List<ExternalHandoffResult>> listCreatedForArtifacts(List<String> sourceTaskIds) {
        if (sourceTaskIds.isEmpty()) {
            return Map.of();
        }
        return handoffRepository.findBySourceTaskIdInAndStatusOrderByCreatedAtDesc(sourceTaskIds, "CREATED").stream()
                .map(ExternalHandoffEntity::toResult)
                .collect(Collectors.groupingBy(ExternalHandoffResult::sourceTaskId));
    }

    private static String requireDestination(String destination) {
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("destination is required: jira, jira-comment, bitbucket, or hermes");
        }
        return destination.trim().toLowerCase();
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String defaultSummary(Artifact<Object> source) {
        String id = source.taskId();
        String shortId = id.length() <= 8 ? id : id.substring(0, 8);
        return "Reviewed " + source.skill() + " artifact " + shortId;
    }

    private String defaultDescription(Artifact<Object> source) {
        StringBuilder sb = new StringBuilder();
        sb.append("Reviewed artifact from Analyst Workbench\n\n");
        sb.append("Task ID: ").append(source.taskId()).append('\n');
        sb.append("Agent: ").append(source.agent()).append('\n');
        sb.append("Skill: ").append(source.skill()).append('\n');
        sb.append("Created at: ").append(source.createdAt()).append('\n');
        sb.append("Reviewed: ").append(source.reviewed()).append("\n\n");
        sb.append("Evidence:\n");
        if (source.evidence().isEmpty()) {
            sb.append("- No evidence citations were attached.\n");
        } else {
            for (Evidence evidence : source.evidence()) {
                sb.append("- ").append(evidence.claim()).append(" [").append(evidence.source()).append("]\n");
            }
        }
        sb.append("\nResult JSON:\n").append(truncateJson(source.result()));
        return sb.toString();
    }

    private String truncateJson(Object result) {
        try {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
            return json.length() > 4000 ? json.substring(0, 4000) + "\n...[truncated]" : json;
        } catch (JsonProcessingException e) {
            return String.valueOf(result);
        }
    }
}
