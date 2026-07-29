package com.miniproject.backend.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniproject.backend.artifact.Artifact;
import com.miniproject.backend.artifact.Evidence;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
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

    public ArtifactPersistenceService(AnalysisArtifactRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public <T> void save(Artifact<T> artifact, String profile, String inputText) {
        save(artifact, profile, inputText, null);
    }

    /** parentTaskId is set when this artifact was created via a deterministic handoff (Section 5.7). */
    @Transactional
    public <T> void save(Artifact<T> artifact, String profile, String inputText, String parentTaskId) {
        try {
            String resultJson = objectMapper.writeValueAsString(artifact.result());
            AnalysisArtifactEntity entity = new AnalysisArtifactEntity(
                    artifact.taskId(), profile, artifact.agent(), artifact.skill(),
                    inputText, resultJson, Instant.parse(artifact.createdAt()), parentTaskId);
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
                        e.isReviewed(), e.getReviewedAt() == null ? null : e.getReviewedAt().toString(), e.getParentTaskId()))
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

    @Transactional
    public Optional<Artifact<Object>> markReviewed(String taskId) {
        return repository.findById(taskId).map(entity -> {
            entity.markReviewed(Instant.now());
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
            boolean reviewed, String reviewedAt, String parentTaskId) {
    }
}
