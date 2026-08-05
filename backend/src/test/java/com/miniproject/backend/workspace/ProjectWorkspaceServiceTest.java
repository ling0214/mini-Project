package com.miniproject.backend.workspace;

import com.miniproject.backend.mcp.ProjectGraphClient;
import com.miniproject.backend.skills.ProjectContextMatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectWorkspaceServiceTest {

    private final ProjectWorkspaceRepository repository = mock(ProjectWorkspaceRepository.class);
    private final ProjectWorkspaceSubpathRepository subpathRepository = mock(ProjectWorkspaceSubpathRepository.class);
    private final ProjectContextMatcher matcher = mock(ProjectContextMatcher.class);
    private final ProjectGraphClient graphClient = mock(ProjectGraphClient.class);
    private final GraphifyIndexService graphifyIndexService = mock(GraphifyIndexService.class);
    private final ProjectWorkspaceService service =
            new ProjectWorkspaceService(repository, subpathRepository, matcher, graphClient, graphifyIndexService);

    @Test
    void declareRejectsMissingLocalPath(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does-not-exist");

        assertThatThrownBy(() -> service.declare("Demo", null, missing.toString()))
                .isInstanceOf(IllegalArgumentException.class);

        verify(repository, never()).save(any());
        verify(matcher, never()).useWorkspace(any(), any());
    }

    @Test
    void declareSavesActivatesAndSyncsMatcher(@TempDir Path tempDir) {
        when(repository.findByActiveTrue()).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectWorkspaceEntity saved = service.declare("Demo", "https://example.com/demo.git", tempDir.toString());

        assertThat(saved.getName()).isEqualTo("Demo");
        assertThat(saved.getRepoUrl()).isEqualTo("https://example.com/demo.git");
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getIndexStatus()).isEqualTo("indexing");
        verify(matcher).useWorkspace(eq("Demo"), eq(tempDir));
        verify(graphClient, timeout(2000)).indexProject(eq(tempDir.toString()), eq("Demo"));
    }

    @Test
    void declareDeactivatesPreviouslyActiveWorkspace(@TempDir Path tempDir) {
        ProjectWorkspaceEntity previous = new ProjectWorkspaceEntity(
                "old-id", "Old Project", null, tempDir.toString(), Instant.now());
        when(repository.findByActiveTrue()).thenReturn(Optional.of(previous));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.declare("New Project", null, tempDir.toString());

        assertThat(previous.isActive()).isFalse();
    }

    @Test
    void activateRejectsUnknownId() {
        when(repository.findById("missing-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.activate("missing-id"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void activateRejectsWorkspaceWhoseLocalPathNoLongerExists(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("gone");
        ProjectWorkspaceEntity target = new ProjectWorkspaceEntity(
                "target-id", "Gone Project", null, missing.toString(), Instant.now());
        when(repository.findById("target-id")).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.activate("target-id"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void activateSwitchesActiveFlagAndSyncsMatcher(@TempDir Path tempDir) {
        ProjectWorkspaceEntity currentlyActive = new ProjectWorkspaceEntity(
                "current-id", "Current Project", null, tempDir.toString(), Instant.now());
        ProjectWorkspaceEntity target = new ProjectWorkspaceEntity(
                "target-id", "Target Project", null, tempDir.toString(), Instant.now());
        target.deactivate();

        when(repository.findById("target-id")).thenReturn(Optional.of(target));
        when(repository.findByActiveTrue()).thenReturn(Optional.of(currentlyActive));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectWorkspaceEntity result = service.activate("target-id");

        assertThat(currentlyActive.isActive()).isFalse();
        assertThat(result.isActive()).isTrue();
        verify(matcher).useWorkspace(eq("Target Project"), eq(tempDir));
    }

    @Test
    void reindexCurrentMarksIndexingAndStartsGraphIndex(@TempDir Path tempDir) {
        ProjectWorkspaceEntity current = new ProjectWorkspaceEntity(
                "current-id", "Current Project", null, tempDir.toString(), Instant.now());
        current.markIndexFailed("previous failure");
        when(repository.findByActiveTrue()).thenReturn(Optional.of(current));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectWorkspaceEntity result = service.reindexCurrent();

        assertThat(result.getIndexStatus()).isEqualTo("indexing");
        assertThat(result.getIndexError()).isNull();
        verify(matcher).useWorkspace(eq("Current Project"), eq(tempDir));
        verify(graphClient, timeout(2000)).indexProject(eq(tempDir.toString()), eq("Current Project"));
    }

    @Test
    void graphifyIndexCurrentMarksIndexingAndStartsGraphifyIndex(@TempDir Path tempDir) {
        ProjectWorkspaceEntity current = new ProjectWorkspaceEntity(
                "current-id", "Current Project", null, tempDir.toString(), Instant.now());
        current.markGraphifyIndexFailed("previous failure");
        when(repository.findByActiveTrue()).thenReturn(Optional.of(current));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectWorkspaceEntity result = service.graphifyIndexCurrent();

        assertThat(result.getGraphifyIndexStatus()).isEqualTo("indexing");
        assertThat(result.getGraphifyIndexError()).isNull();
        verify(graphifyIndexService, timeout(2000)).indexCodeOnly(eq(tempDir));
    }

    @Test
    void graphifyIndexCurrentReusesExistingGraphOutput(@TempDir Path tempDir) {
        ProjectWorkspaceEntity current = new ProjectWorkspaceEntity(
                "current-id", "Current Project", null, tempDir.toString(), Instant.now());
        current.markGraphifyIndexFailed("previous failure");
        when(repository.findByActiveTrue()).thenReturn(Optional.of(current));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(graphifyIndexService.hasGraphOutput(tempDir)).thenReturn(true);

        ProjectWorkspaceEntity result = service.graphifyIndexCurrent();

        assertThat(result.getGraphifyIndexStatus()).isEqualTo("ready");
        assertThat(result.getGraphifyIndexError()).isNull();
        verify(graphifyIndexService, never()).indexCodeOnly(any());
    }

    @Test
    void currentReconcilesGraphifyReadyWhenGraphOutputExists(@TempDir Path tempDir) {
        ProjectWorkspaceEntity current = new ProjectWorkspaceEntity(
                "current-id", "Current Project", null, tempDir.toString(), Instant.now());
        current.markGraphifyIndexFailed("previous failure");
        when(repository.findByActiveTrue()).thenReturn(Optional.of(current));
        when(graphifyIndexService.hasGraphOutput(tempDir)).thenReturn(true);

        Optional<ProjectWorkspaceEntity> result = service.current();

        assertThat(result).isPresent();
        assertThat(result.get().getGraphifyIndexStatus()).isEqualTo("ready");
        verify(repository).save(current);
    }

    @Test
    void currentMarksStaleGraphifyIndexingAsFailed(@TempDir Path tempDir) {
        ProjectWorkspaceEntity current = new ProjectWorkspaceEntity(
                "current-id", "Current Project", null, tempDir.toString(), Instant.now());
        current.markGraphifyIndexing();
        when(repository.findByActiveTrue()).thenReturn(Optional.of(current));
        when(graphifyIndexService.hasGraphOutput(tempDir)).thenReturn(false);

        Optional<ProjectWorkspaceEntity> result = service.current();

        assertThat(result).isPresent();
        assertThat(result.get().getGraphifyIndexStatus()).isEqualTo("failed");
        assertThat(result.get().getGraphifyIndexError()).contains("did not finish");
        verify(repository).save(current);
    }

    @Test
    void graphifyIndexAtPathIndexesTheGivenSubfolderInsteadOfLocalPath(@TempDir Path tempDir) throws java.io.IOException {
        Path subFolder = java.nio.file.Files.createDirectory(tempDir.resolve("pruserveplus-ipad"));
        ProjectWorkspaceEntity current = new ProjectWorkspaceEntity(
                "current-id", "PSP Frontend", null, tempDir.toString(), Instant.now());
        current.markGraphifyIndexFailed("No supported code folders found for Graphify indexing.");
        when(repository.findByActiveTrue()).thenReturn(Optional.of(current));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectWorkspaceEntity result = service.graphifyIndexAtPath(subFolder.toString());

        assertThat(result.getGraphifyIndexStatus()).isEqualTo("indexing");
        assertThat(result.getGraphifyIndexPath()).isEqualTo(subFolder.toString());
        assertThat(result.getLocalPath()).isEqualTo(tempDir.toString());
        verify(graphifyIndexService, timeout(2000)).indexCodeOnly(eq(subFolder));
    }

    @Test
    void graphifyIndexAtPathRejectsNonDirectory(@TempDir Path tempDir) {
        Path notADirectory = tempDir.resolve("does-not-exist");
        ProjectWorkspaceEntity current = new ProjectWorkspaceEntity(
                "current-id", "PSP Frontend", null, tempDir.toString(), Instant.now());
        when(repository.findByActiveTrue()).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.graphifyIndexAtPath(notADirectory.toString()))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void graphifyIndexCurrentUsesOverridePathOnceOneIsSet(@TempDir Path tempDir) throws java.io.IOException {
        Path subFolder = java.nio.file.Files.createDirectory(tempDir.resolve("pruserve-backoffice"));
        ProjectWorkspaceEntity current = new ProjectWorkspaceEntity(
                "current-id", "PSP Backend", null, tempDir.toString(), Instant.now());
        current.setGraphifyIndexPath(subFolder.toString());
        when(repository.findByActiveTrue()).thenReturn(Optional.of(current));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.graphifyIndexCurrent();

        verify(graphifyIndexService, timeout(2000)).indexCodeOnly(eq(subFolder));
    }

    @Test
    void removeDeletesTheDeclaredWorkspace(@TempDir Path tempDir) {
        ProjectWorkspaceEntity target = new ProjectWorkspaceEntity(
                "target-id", "Target Project", null, tempDir.toString(), Instant.now());
        when(repository.findById("target-id")).thenReturn(Optional.of(target));

        service.remove("target-id");

        org.mockito.Mockito.verify(repository).delete(target);
    }

    @Test
    void removeRejectsUnknownId() {
        when(repository.findById("missing-id")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remove("missing-id"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void indexSubpathSanitizesTheGeneratedProjectNameForCodebaseMemoryMcp(@TempDir Path tempDir) throws IOException {
        // codebase-memory-mcp normalizes spaces/punctuation differently between
        // index_repository and get_architecture (a real bug hit against a
        // workspace named "Admin Console (test)" -- indexed under
        // "Admin-Console-test-Frontend" but then 404'd when queried as
        // "Admin_Console_(test)_::_Frontend"). Pre-sanitizing must produce the
        // same hyphen-only shape both tools already agree on.
        ProjectWorkspaceEntity workspace = new ProjectWorkspaceEntity(
                "workspace-id", "Admin Console (test)", null, tempDir.toString(), Instant.now());
        when(repository.findById("workspace-id")).thenReturn(Optional.of(workspace));

        Path subpathDir = Files.createDirectory(tempDir.resolve("webapp"));
        ProjectWorkspaceSubpathEntity subpath = new ProjectWorkspaceSubpathEntity(
                "subpath-id", "workspace-id", "Frontend", subpathDir.toString(), Instant.now());
        when(subpathRepository.findById("subpath-id")).thenReturn(Optional.of(subpath));
        when(subpathRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ProjectWorkspaceSubpathEntity result = service.indexSubpath("workspace-id", "subpath-id");

        assertThat(result.getIndexedProjectName()).isEqualTo("Admin-Console-test-Frontend");
        verify(graphClient, timeout(2000)).indexProject(subpathDir.toString(), "Admin-Console-test-Frontend");
    }
}
