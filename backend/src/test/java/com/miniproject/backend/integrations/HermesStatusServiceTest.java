package com.miniproject.backend.integrations;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HermesStatusServiceTest {

    @Test
    void rejectsStatusOutsideFixedVocabulary() {
        HermesStatusRepository repository = mock(HermesStatusRepository.class);
        HermesStatusService service = new HermesStatusService(repository);

        assertThatThrownBy(() -> service.recordStatus("task-1", "Made up status", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be one of");
        verify(repository, never()).save(any());
    }

    @Test
    void rejectsBlankSourceTaskId() {
        HermesStatusRepository repository = mock(HermesStatusRepository.class);
        HermesStatusService service = new HermesStatusService(repository);

        assertThatThrownBy(() -> service.recordStatus(" ", "Sent to Hermes", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source_task_id");
    }

    @Test
    void supersedesPreviousCurrentRowWhenRecordingNewStatus() {
        HermesStatusRepository repository = mock(HermesStatusRepository.class);
        HermesStatusService service = new HermesStatusService(repository);
        HermesStatusEntity previous = new HermesStatusEntity("task-1", "Sent to Hermes", null, "PSP", null);
        when(repository.findBySourceTaskIdAndDeleteDateIsNull("task-1")).thenReturn(Optional.of(previous));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        HermesStatusView result = service.recordStatus("task-1", "Hermes accepted", "Picked up by RCA agent.", null, null);

        assertThat(previous.toView().deleteDate()).isNotNull();
        assertThat(result.status()).isEqualTo("Hermes accepted");
        assertThat(result.sourceTaskId()).isEqualTo("task-1");
        assertThat(result.note()).isEqualTo("Picked up by RCA agent.");
    }

    @Test
    void inheritsProjectFromPreviousRowWhenNewReportDoesNotRepeatIt() {
        HermesStatusRepository repository = mock(HermesStatusRepository.class);
        HermesStatusService service = new HermesStatusService(repository);
        HermesStatusEntity previous = new HermesStatusEntity("task-1", "Sent to Hermes", null, "PSP", null);
        when(repository.findBySourceTaskIdAndDeleteDateIsNull("task-1")).thenReturn(Optional.of(previous));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        HermesStatusView result = service.recordStatus("task-1", "Hermes accepted", "picked up", null, null);

        assertThat(result.project()).isEqualTo("PSP");
    }

    @Test
    void explicitProjectOverridesInheritedOne() {
        HermesStatusRepository repository = mock(HermesStatusRepository.class);
        HermesStatusService service = new HermesStatusService(repository);
        HermesStatusEntity previous = new HermesStatusEntity("task-1", "Sent to Hermes", null, "PSP", null);
        when(repository.findBySourceTaskIdAndDeleteDateIsNull("task-1")).thenReturn(Optional.of(previous));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        HermesStatusView result = service.recordStatus("task-1", "Hermes accepted", "picked up", "MyBanjirCare", null);

        assertThat(result.project()).isEqualTo("MyBanjirCare");
    }

    @Test
    void inheritsSimilarIssuesFromPreviousRowWhenLaterReportsDoNotRepeatIt() {
        HermesStatusRepository repository = mock(HermesStatusRepository.class);
        HermesStatusService service = new HermesStatusService(repository);
        HermesStatusEntity previous = new HermesStatusEntity(
                "task-1", "Hermes accepted", null, "PSP", "## Local Case Memory Results\n- PRUW00357944 confidence 8");
        when(repository.findBySourceTaskIdAndDeleteDateIsNull("task-1")).thenReturn(Optional.of(previous));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        HermesStatusView result = service.recordStatus("task-1", "Developer update", "RCA posted", null, null);

        assertThat(result.similarIssues()).contains("PRUW00357944");
    }

    @Test
    void currentForAllTasksTrimsTrailingSlashBeforeFiltering() {
        HermesStatusRepository repository = mock(HermesStatusRepository.class);
        HermesStatusService service = new HermesStatusService(repository);
        HermesStatusEntity match = new HermesStatusEntity(
                "task-1", "Hermes accepted", "picked up", "C:/Users/lingn/Inglab Project", null);
        HermesStatusEntity other = new HermesStatusEntity(
                "task-2", "Hermes accepted", "picked up", "C:/Users/lingn/MyBanjirCare", null);
        when(repository.findByDeleteDateIsNullOrderByCreateDateDesc()).thenReturn(List.of(match, other));

        List<HermesStatusView> result = service.currentForAllTasks("C:/Users/lingn/Inglab Project/");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).project()).isEqualTo("C:/Users/lingn/Inglab Project");
    }

    @Test
    void currentForAllTasksMatchesSubfolderWorkspaceAgainstRepoRootProject() {
        HermesStatusRepository repository = mock(HermesStatusRepository.class);
        HermesStatusService service = new HermesStatusService(repository);
        HermesStatusEntity rootTagged = new HermesStatusEntity(
                "task-1", "Developer update", "RCA posted", "C:/Users/lingn/Inglab Project", null);
        when(repository.findByDeleteDateIsNullOrderByCreateDateDesc()).thenReturn(List.of(rootTagged));

        List<HermesStatusView> frontendWorkspace = service.currentForAllTasks(
                "C:/Users/lingn/Inglab Project/pruserveplus-ipad");
        List<HermesStatusView> unrelatedWorkspace = service.currentForAllTasks(
                "C:/Users/lingn/Inglab ProjectX");

        assertThat(frontendWorkspace).hasSize(1);
        assertThat(unrelatedWorkspace).isEmpty();
    }

    @Test
    void listsHistoryMostRecentFirst() {
        HermesStatusRepository repository = mock(HermesStatusRepository.class);
        HermesStatusService service = new HermesStatusService(repository);
        HermesStatusEntity newest = new HermesStatusEntity("task-1", "Developer update", "fix in progress", "PSP", null);
        when(repository.findBySourceTaskIdOrderByCreateDateDesc("task-1")).thenReturn(List.of(newest));

        List<HermesStatusView> history = service.history("task-1");

        assertThat(history).hasSize(1);
        assertThat(history.get(0).status()).isEqualTo("Developer update");
    }
}
