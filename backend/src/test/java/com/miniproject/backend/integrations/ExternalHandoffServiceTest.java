package com.miniproject.backend.integrations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miniproject.backend.artifact.Artifact;
import com.miniproject.backend.artifact.Evidence;
import com.miniproject.backend.persistence.ArtifactPersistenceService;
import com.miniproject.backend.workspace.ProjectWorkspaceEntity;
import com.miniproject.backend.workspace.ProjectWorkspaceService;
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
        HermesConnector hermes = mock(HermesConnector.class);
        HermesStatusService hermesStatus = mock(HermesStatusService.class);
        ProjectWorkspaceService workspace = mock(ProjectWorkspaceService.class);
        ExternalHandoffService service = new ExternalHandoffService(
                persistence, repository, jira, bitbucket, hermes, hermesStatus, workspace, new ObjectMapper());

        when(persistence.findArtifact("task-1")).thenReturn(Optional.of(artifact(false)));

        assertThatThrownBy(() -> service.handoff(
                "task-1", new ExternalHandoffRequest("jira", null, null, null, null, true)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be reviewed");
        verify(jira, never()).createIssue(any(), any(), anyBoolean());
        verify(jira, never()).commentOnIssue(any(), any(), anyBoolean());
        verify(hermes, never()).sendTask(any(), any(), anyBoolean());
        verify(repository, never()).save(any());
    }

    @Test
    void recordsReviewedJiraDryRunHandoff() {
        ArtifactPersistenceService persistence = mock(ArtifactPersistenceService.class);
        ExternalHandoffRepository repository = mock(ExternalHandoffRepository.class);
        JiraConnector jira = mock(JiraConnector.class);
        BitbucketConnector bitbucket = mock(BitbucketConnector.class);
        HermesConnector hermes = mock(HermesConnector.class);
        HermesStatusService hermesStatus = mock(HermesStatusService.class);
        ProjectWorkspaceService workspace = mock(ProjectWorkspaceService.class);
        ExternalHandoffService service = new ExternalHandoffService(
                persistence, repository, jira, bitbucket, hermes, hermesStatus, workspace, new ObjectMapper());

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
        HermesConnector hermes = mock(HermesConnector.class);
        HermesStatusService hermesStatus = mock(HermesStatusService.class);
        ProjectWorkspaceService workspace = mock(ProjectWorkspaceService.class);
        ExternalHandoffService service = new ExternalHandoffService(
                persistence, repository, jira, bitbucket, hermes, hermesStatus, workspace, new ObjectMapper());

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

    @Test
    void recordsReviewedHermesDryRunHandoffWithoutTrackingStatus() {
        ArtifactPersistenceService persistence = mock(ArtifactPersistenceService.class);
        ExternalHandoffRepository repository = mock(ExternalHandoffRepository.class);
        JiraConnector jira = mock(JiraConnector.class);
        BitbucketConnector bitbucket = mock(BitbucketConnector.class);
        HermesConnector hermes = mock(HermesConnector.class);
        HermesStatusService hermesStatus = mock(HermesStatusService.class);
        ProjectWorkspaceService workspace = mock(ProjectWorkspaceService.class);
        ExternalHandoffService service = new ExternalHandoffService(
                persistence, repository, jira, bitbucket, hermes, hermesStatus, workspace, new ObjectMapper());

        when(persistence.findArtifact("task-1")).thenReturn(Optional.of(artifact(true)));
        when(hermes.sendTask(any(), any(), anyBoolean())).thenReturn(
                new ConnectorResult("DRY_RUN", "Hermes Discord intake", null, "ready", true));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ExternalHandoffResult result = service.handoff(
                "task-1", new ExternalHandoffRequest("hermes", "Summary", "Draft task", null, null, true));

        assertThat(result.sourceTaskId()).isEqualTo("task-1");
        assertThat(result.destination()).isEqualTo("hermes");
        assertThat(result.status()).isEqualTo("DRY_RUN");
        assertThat(result.externalKey()).isEqualTo("Hermes Discord intake");
        verify(hermes).sendTask("Summary", "Draft task", true);
        verify(repository).save(any());
        // Dry-run never actually reached Hermes, so nothing should be tracked yet.
        verify(hermesStatus, never()).recordStatus(any(), any(), any(), any(), any());
    }

    @Test
    void recordsSentToHermesStatusTaggedWithActiveProjectOnRealHandoff() {
        ArtifactPersistenceService persistence = mock(ArtifactPersistenceService.class);
        ExternalHandoffRepository repository = mock(ExternalHandoffRepository.class);
        JiraConnector jira = mock(JiraConnector.class);
        BitbucketConnector bitbucket = mock(BitbucketConnector.class);
        HermesConnector hermes = mock(HermesConnector.class);
        HermesStatusService hermesStatus = mock(HermesStatusService.class);
        ProjectWorkspaceService workspace = mock(ProjectWorkspaceService.class);
        ExternalHandoffService service = new ExternalHandoffService(
                persistence, repository, jira, bitbucket, hermes, hermesStatus, workspace, new ObjectMapper());

        when(persistence.findArtifact("task-1")).thenReturn(Optional.of(artifact(true)));
        when(hermes.sendTask(any(), any(), anyBoolean())).thenReturn(
                new ConnectorResult("SENT", "Hermes Discord intake", null, "sent", false));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        ProjectWorkspaceEntity activeWorkspace = mock(ProjectWorkspaceEntity.class);
        when(activeWorkspace.getLocalPath()).thenReturn("C:/Users/lingn/Inglab Project");
        when(workspace.current()).thenReturn(Optional.of(activeWorkspace));

        service.handoff("task-1", new ExternalHandoffRequest("hermes", "Summary", "Real task", null, null, false));

        verify(hermesStatus).recordStatus(
                "task-1", "Sent to Hermes", "Reviewed handoff package sent to Hermes intake.", "C:/Users/lingn/Inglab Project", null);
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
