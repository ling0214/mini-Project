package com.miniproject.backend.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniproject.backend.artifact.Artifact;
import com.miniproject.backend.artifact.Evidence;
import com.miniproject.backend.memory.MemoryCardService;
import com.miniproject.backend.workspace.ProjectWorkspaceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Persists every skill Artifact as an AnalysisArtifactEntity (Phase 3.5,
 * docs/proposal.md). CoordinatorService calls save() right after building
 * each Artifact, so persistence is a side effect at the same seam for every
 * skill rather than bolted on per-controller.
 */
@Service
public class ArtifactPersistenceService {

    private final AnalysisArtifactRepository repository;
    private final ObjectMapper objectMapper;
    private final MemoryCardService memoryCardService;
    private final ProjectWorkspaceService projectWorkspaceService;

    public ArtifactPersistenceService(
            AnalysisArtifactRepository repository,
            ObjectMapper objectMapper,
            MemoryCardService memoryCardService,
            ProjectWorkspaceService projectWorkspaceService) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.memoryCardService = memoryCardService;
        this.projectWorkspaceService = projectWorkspaceService;
    }

    public <T> void save(Artifact<T> artifact, String profile, String inputText) {
        save(artifact, profile, inputText, null);
    }

    /** parentTaskId is set when this artifact was created via a deterministic handoff (Section 5.7). */
    @Transactional
    public <T> void save(Artifact<T> artifact, String profile, String inputText, String parentTaskId) {
        try {
            String resultJson = objectMapper.writeValueAsString(artifact.result());
            String projectPath = projectWorkspaceService.current().map(w -> w.getLocalPath()).orElse(null);
            AnalysisArtifactEntity entity = new AnalysisArtifactEntity(
                    artifact.taskId(), profile, artifact.agent(), artifact.skill(),
                    inputText, resultJson, Instant.parse(artifact.createdAt()), parentTaskId, projectPath);
            for (Evidence e : artifact.evidence()) {
                entity.addEvidence(e.claim(), e.source());
            }
            repository.save(entity);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to persist artifact " + artifact.taskId(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<ArtifactSummary> listSummaries() {
        return repository.findAllByOrderByCreatedAtDesc().stream()
                .map(e -> new ArtifactSummary(
                        e.getTaskId(), e.getProfile(), e.getSkill(), truncate(e.getInputText()), e.getCreatedAt().toString(),
                        e.isReviewed(), e.getReviewedAt() == null ? null : e.getReviewedAt().toString(),
                        e.getParentTaskId(), e.getProjectPath()))
                .toList();
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 140 ? oneLine.substring(0, 140) + "…" : oneLine;
    }

    @Transactional(readOnly = true)
    public Optional<Artifact<Object>> findArtifact(String taskId) {
        return repository.findById(taskId).map(this::toArtifact);
    }

    @Transactional(readOnly = true)
    public Optional<String> findInputText(String taskId) {
        return repository.findById(taskId).map(AnalysisArtifactEntity::getInputText);
    }

    /** Every artifact created via a handoff (Section 5.7) from parentTaskId, of any skill. */
    @Transactional(readOnly = true)
    public List<Artifact<Object>> findChildren(String parentTaskId) {
        return repository.findByParentTaskId(parentTaskId).stream().map(this::toArtifact).toList();
    }

    /**
     * Walks parentTaskId upward from taskId to the root, oldest-first. Safe to
     * call on any artifact chain (not just requirement-analysis/clarify) since
     * it only follows links, but requirement-analysis is the only skill that
     * currently links to a same-skill parent (Section: clarifyRequirementAnalysis).
     */
    @Transactional(readOnly = true)
    public List<ChainEntry> findClarificationChain(String taskId) {
        List<ChainEntry> chain = new ArrayList<>();
        String currentId = taskId;
        while (currentId != null) {
            String lookupId = currentId;
            AnalysisArtifactEntity entity = repository.findById(lookupId)
                    .orElseThrow(() -> new NoSuchElementException("No artifact found for task_id " + lookupId));
            chain.add(new ChainEntry(
                    entity.getTaskId(), entity.getSkill(), entity.getCreatedAt(), entity.isReviewed(),
                    entity.getInputText(), entity.getResultJson()));
            currentId = entity.getParentTaskId();
        }
        Collections.reverse(chain);
        return chain;
    }

    public record ChainEntry(
            String taskId, String skill, Instant createdAt, boolean reviewed, String inputText, String resultJson) {
    }

    @Transactional
    public Optional<Artifact<Object>> markReviewed(String taskId) {
        return repository.findById(taskId).map(entity -> {
            entity.markReviewed(Instant.now());
            memoryCardService.recordReviewed(entity.getTaskId(), entity.getSkill(), entity.getResultJson());
            return toArtifact(entity);
        });
    }

    private Artifact<Object> toArtifact(AnalysisArtifactEntity entity) {
        Object result;
        try {
            result = objectMapper.readValue(entity.getResultJson(), Object.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Corrupt result_json for artifact " + entity.getTaskId(), e);
        }
        List<Evidence> evidence = entity.getEvidence().stream()
                .map(ev -> new Evidence(ev.getClaim(), ev.getSource()))
                .toList();
        return new Artifact<>("artifact.v1", entity.getAgent(), entity.getSkill(), entity.getTaskId(),
                entity.getParentTaskId(), entity.getCreatedAt().toString(), result, evidence, entity.isReviewed());
    }

    public record ArtifactSummary(
            String taskId, String profile, String skill, String inputPreview, String createdAt,
            boolean reviewed, String reviewedAt, String parentTaskId, String projectPath) {
    }
}
