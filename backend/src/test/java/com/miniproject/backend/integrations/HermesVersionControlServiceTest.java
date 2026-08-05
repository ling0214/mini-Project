package com.miniproject.backend.integrations;

import com.miniproject.backend.artifact.Artifact;
import com.miniproject.backend.artifact.Evidence;
import com.miniproject.backend.persistence.ArtifactPersistenceService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HermesVersionControlServiceTest {

    @Test
    void reportsCleanWorkingTree() {
        ArtifactPersistenceService persistence = mock(ArtifactPersistenceService.class);
        GitLogReader gitLogReader = mock(GitLogReader.class);
        when(gitLogReader.isWorkingTreeClean("C:/repos/hermes-agent")).thenReturn(true);
        HermesVersionControlService service = new HermesVersionControlService(persistence, gitLogReader);

        var status = service.checkStatus("C:/repos/hermes-agent");

        assertThat(status.clean()).isTrue();
    }

    @Test
    void rejectsPullUntilArtifactIsReviewed() {
        ArtifactPersistenceService persistence = mock(ArtifactPersistenceService.class);
        GitLogReader gitLogReader = mock(GitLogReader.class);
        when(persistence.findArtifact("task-1")).thenReturn(Optional.of(artifact(false)));
        HermesVersionControlService service = new HermesVersionControlService(persistence, gitLogReader);

        assertThatThrownBy(() -> service.pull("task-1", "C:/repos/hermes-agent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be reviewed");
        verify(gitLogReader, never()).pull(anyString());
    }

    @Test
    void rejectsPullWhenWorkingTreeIsDirty() {
        ArtifactPersistenceService persistence = mock(ArtifactPersistenceService.class);
        GitLogReader gitLogReader = mock(GitLogReader.class);
        when(persistence.findArtifact("task-1")).thenReturn(Optional.of(artifact(true)));
        when(gitLogReader.isWorkingTreeClean("C:/repos/hermes-agent")).thenReturn(false);
        HermesVersionControlService service = new HermesVersionControlService(persistence, gitLogReader);

        assertThatThrownBy(() -> service.pull("task-1", "C:/repos/hermes-agent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("uncommitted changes");
        verify(gitLogReader, never()).pull(anyString());
    }

    @Test
    void pullsWhenReviewedAndClean() {
        ArtifactPersistenceService persistence = mock(ArtifactPersistenceService.class);
        GitLogReader gitLogReader = mock(GitLogReader.class);
        when(persistence.findArtifact("task-1")).thenReturn(Optional.of(artifact(true)));
        when(gitLogReader.isWorkingTreeClean("C:/repos/hermes-agent")).thenReturn(true);
        when(gitLogReader.pull("C:/repos/hermes-agent")).thenReturn("Fast-forward\n 3 files changed");
        HermesVersionControlService service = new HermesVersionControlService(persistence, gitLogReader);

        var result = service.pull("task-1", "C:/repos/hermes-agent");

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("Fast-forward");
    }

    private static Artifact<Object> artifact(boolean reviewed) {
        return new Artifact<>(
                "artifact.v1",
                "software-analyst-agent",
                "hermes-version-advisor",
                "task-1",
                null,
                Instant.now().toString(),
                Map.of("recommended_ref", "v2026.8.3"),
                List.of(new Evidence("5 commits behind", "hermes-version-advisor")),
                reviewed);
    }
}
