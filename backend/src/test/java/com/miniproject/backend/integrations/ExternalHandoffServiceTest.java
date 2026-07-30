package com.miniproject.backend.integrations;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExternalHandoffServiceTest {

    @Test
    void rejectsExternalHandoffUntilArtifactIsReviewed() {
        ArtifactPersistenceService persistence = mock(ArtifactPersistenceService.class);
        ExternalHandoffRepository repository = mock(ExternalHandoffRepository.class);
        JiraConnector jira = mock(JiraConnector.class);
        BitbucketConnector bitbucket = mock(BitbucketConnector.class);
        ExternalHandoffService service = new ExternalHandoffService(
                persistence, repository, jira, bitbucket, new ObjectMapper());

        when(persistence.findArtifact("task-1")).thenReturn(Optional.of(artifact(false)));

        assertThatThrownBy(() -> service.handoff(
                "task-1", new ExternalHandoffRequest("jira", null, null, null, null, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be reviewed");
        verify(jira, never()).createIssue(any(), any(), anyBoolean());
        verify(jira, never()).commentOnIssue(any(), any(), anyBoolean());
        verify(repository, never()).save(any());
    }

    @Test
    void recordsReviewedJiraDryRunHandoff() {
        ArtifactPersistenceService persistence = mock(ArtifactPersistenceService.class);
        ExternalHandoffRepository repository = mock(ExternalHandoffRepository.class);
        JiraConnector jira = mock(JiraConnector.class);
        BitbucketConnector bitbucket = mock(BitbucketConnector.class);
        ExternalHandoffService service = new ExternalHandoffService(
                persistence, repository, jira, bitbucket, new ObjectMapper());

        when(persistence.findArtifact("task-1")).thenReturn(Optional.of(artifact(true)));
        when(jira.createIssue(any(), any(), anyBoolean())).thenReturn(
                new ConnectorResult("DRY_RUN", null, null, "ready", true));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ExternalHandoffResult result = service.handoff(
                "task-1", new ExternalHandoffRequest("jira", "Summary", null, null, null, true));

        assertThat(result.sourceTaskId()).isEqualTo("task-1");
        assertThat(result.destination()).isEqualTo("jira");
        assertThat(result.status()).isEqualTo("DRY_RUN");
        assertThat(result.dryRun()).isTrue();
        verify(repository).save(any());
    }

    @Test
    void recordsReviewedJiraCommentDryRunHandoff() {
        ArtifactPersistenceService persistence = mock(ArtifactPersistenceService.class);
        ExternalHandoffRepository repository = mock(ExternalHandoffRepository.class);
        JiraConnector jira = mock(JiraConnector.class);
        BitbucketConnector bitbucket = mock(BitbucketConnector.class);
        ExternalHandoffService service = new ExternalHandoffService(
                persistence, repository, jira, bitbucket, new ObjectMapper());

        when(persistence.findArtifact("task-1")).thenReturn(Optional.of(artifact(true)));
        when(jira.commentOnIssue(any(), any(), anyBoolean())).thenReturn(
                new ConnectorResult("DRY_RUN", "KAN-1", "https://example.atlassian.net/browse/KAN-1", "ready", true));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ExternalHandoffResult result = service.handoff(
                "task-1", new ExternalHandoffRequest("jira-comment", "Summary", "Draft comment", null, "KAN-1", true));

        assertThat(result.sourceTaskId()).isEqualTo("task-1");
        assertThat(result.destination()).isEqualTo("jira-comment");
        assertThat(result.status()).isEqualTo("DRY_RUN");
        assertThat(result.externalKey()).isEqualTo("KAN-1");
        verify(jira).commentOnIssue("KAN-1", "Draft comment", true);
        verify(jira, never()).createIssue(any(), any(), anyBoolean());
        verify(repository).save(any());
    }

    private static Artifact<Object> artifact(boolean reviewed) {
        return new Artifact<>(
                "artifact.v1",
                "project-analyst-agent",
                "impact-analysis",
                "task-1",
                null,
                Instant.now().toString(),
                Map.of("risk_level", "medium"),
                List.of(new Evidence("charge_card is affected", "payments.py:1")),
                reviewed);
    }
}
