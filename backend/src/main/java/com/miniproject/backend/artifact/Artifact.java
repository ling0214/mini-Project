package com.miniproject.backend.artifact;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The artifact.v1 handoff envelope from agents/coordinator-agent.md.
 * `reviewed` starts false for every skill output and only a human accepting
 * it in the UI is allowed to flip it — enforced by callers, not by this
 * record itself.
 */
public record Artifact<T>(
        String schemaVersion,
        String agent,
        String skill,
        String taskId,
        String createdAt,
        T result,
        List<Evidence> evidence,
        boolean reviewed) {

    public static <T> Artifact<T> draft(String agent, String skill, T result, List<Evidence> evidence) {
        return new Artifact<>(
                "artifact.v1",
                agent,
                skill,
                UUID.randomUUID().toString(),
                Instant.now().toString(),
                result,
                evidence,
                false);
    }
}
