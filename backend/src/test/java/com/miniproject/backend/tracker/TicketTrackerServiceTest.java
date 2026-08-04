package com.miniproject.backend.tracker;

import com.miniproject.backend.integrations.ExternalHandoffResult;
import com.miniproject.backend.integrations.ExternalHandoffService;
import com.miniproject.backend.integrations.HermesStatusService;
import com.miniproject.backend.integrations.HermesStatusView;
import com.miniproject.backend.persistence.ArtifactPersistenceService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TicketTrackerServiceTest {

    private static ArtifactPersistenceService.ArtifactSummary artifact(
            String taskId, String skill, String createdAt, boolean reviewed, String parentTaskId) {
        return artifact(taskId, skill, createdAt, reviewed, parentTaskId, "C:/Users/lingn/Inglab Project");
    }

    private static ArtifactPersistenceService.ArtifactSummary artifact(
            String taskId, String skill, String createdAt, boolean reviewed, String parentTaskId, String projectPath) {
        return new ArtifactPersistenceService.ArtifactSummary(
                taskId, "software-analyst", skill, "preview text", createdAt, reviewed, null, parentTaskId, projectPath);
    }

    @Test
    void freshTicketHasOnlyRequirementReviewActive() {
        ArtifactPersistenceService persistence = mock(ArtifactPersistenceService.class);
        HermesStatusService hermes = mock(HermesStatusService.class);
        ExternalHandoffService handoff = mock(ExternalHandoffService.class);

        when(persistence.listSummaries()).thenReturn(List.of(
                artifact("t1", "requirement-analysis", "2026-01-01T00:00:00Z", false, null)));
        when(hermes.currentForAllTasks()).thenReturn(List.of());
        when(handoff.listForArtifact(any())).thenReturn(List.of());

        TicketTrackerService service = new TicketTrackerService(persistence, hermes, handoff);
        List<TicketTrackerView> tickets = service.listTickets();

        assertThat(tickets).hasSize(1);
        List<TicketPhaseView> phases = tickets.get(0).phases();
        assertThat(phases.get(0).state()).isEqualTo("active");
        assertThat(phases.get(1).state()).isEqualTo("pending");
        assertThat(tickets.get(0).ticketType()).isEqualTo("change_request");
    }

    @Test
    void impactAnalysisShowsSkippedWhenTicketWentStraightToHermes() {
        ArtifactPersistenceService persistence = mock(ArtifactPersistenceService.class);
        HermesStatusService hermes = mock(HermesStatusService.class);
        ExternalHandoffService handoff = mock(ExternalHandoffService.class);

        when(persistence.listSummaries()).thenReturn(List.of(
                artifact("t1", "requirement-analysis", "2026-01-01T00:00:00Z", true, null)));
        when(hermes.currentForAllTasks()).thenReturn(List.of(
                new HermesStatusView("h1", "t1", "Hermes accepted", "picked up", "PSP", null, "2026-01-02T00:00:00Z", null)));
        when(handoff.listForArtifact(any())).thenReturn(List.of());

        TicketTrackerService service = new TicketTrackerService(persistence, hermes, handoff);
        List<TicketPhaseView> phases = service.listTickets().get(0).phases();

        assertThat(phases.get(0).state()).isEqualTo("done");
        assertThat(phases.get(1).state()).isEqualTo("skipped");
        assertThat(phases.get(2).state()).isEqualTo("active");
        assertThat(service.listTickets().get(0).ticketType()).isEqualTo("issue");
    }

    @Test
    void impactAnalysisReviewedTakesPrecedenceOverSkip() {
        ArtifactPersistenceService persistence = mock(ArtifactPersistenceService.class);
        HermesStatusService hermes = mock(HermesStatusService.class);
        ExternalHandoffService handoff = mock(ExternalHandoffService.class);

        when(persistence.listSummaries()).thenReturn(List.of(
                artifact("t1", "requirement-analysis", "2026-01-01T00:00:00Z", true, null),
                artifact("t2", "impact-analysis", "2026-01-02T00:00:00Z", true, "t1")));
        when(hermes.currentForAllTasks()).thenReturn(List.of(
                new HermesStatusView("h1", "t2", "Hermes accepted", "picked up", "PSP", null, "2026-01-03T00:00:00Z", null)));
        when(handoff.listForArtifact(any())).thenReturn(List.of());

        TicketTrackerService service = new TicketTrackerService(persistence, hermes, handoff);
        List<TicketTrackerView> tickets = service.listTickets();

        assertThat(tickets.get(0).phases().get(1).state()).isEqualTo("done");
        assertThat(tickets.get(0).ticketType()).isEqualTo("change_request");
    }

    @Test
    void fullyCompletedTicketHasAllPhasesDone() {
        ArtifactPersistenceService persistence = mock(ArtifactPersistenceService.class);
        HermesStatusService hermes = mock(HermesStatusService.class);
        ExternalHandoffService handoff = mock(ExternalHandoffService.class);

        when(persistence.listSummaries()).thenReturn(List.of(
                artifact("t1", "requirement-analysis", "2026-01-01T00:00:00Z", true, null),
                artifact("t2", "impact-analysis", "2026-01-02T00:00:00Z", true, "t1"),
                artifact("t3", "handoff-summary", "2026-01-05T00:00:00Z", true, "t2")));
        when(hermes.currentForAllTasks()).thenReturn(List.of(
                new HermesStatusView("h1", "t2", "Close summary", "shipped", "PSP", null, "2026-01-04T00:00:00Z", null)));
        when(handoff.listForArtifact("t1")).thenReturn(List.of());
        when(handoff.listForArtifact("t2")).thenReturn(List.of());
        when(handoff.listForArtifact("t3")).thenReturn(List.of(
                new ExternalHandoffResult("e1", "t3", "jira", "CREATED", "PROJ-9", "https://jira.example/PROJ-9",
                        "ok", false, "2026-01-06T00:00:00Z")));

        TicketTrackerService service = new TicketTrackerService(persistence, hermes, handoff);
        List<TicketPhaseView> phases = service.listTickets().get(0).phases();

        assertThat(phases).allSatisfy(p -> assertThat(p.state()).isEqualTo("done"));
    }

    @Test
    void filtersTicketsByProjectPathAndIgnoresOthers() {
        ArtifactPersistenceService persistence = mock(ArtifactPersistenceService.class);
        HermesStatusService hermes = mock(HermesStatusService.class);
        ExternalHandoffService handoff = mock(ExternalHandoffService.class);

        when(persistence.listSummaries()).thenReturn(List.of(
                artifact("t1", "requirement-analysis", "2026-01-01T00:00:00Z", true, null, "C:/Users/lingn/Inglab Project"),
                artifact("t2", "requirement-analysis", "2026-01-02T00:00:00Z", true, null, "C:/Users/lingn/MyBanjirCare")));
        when(hermes.currentForAllTasks()).thenReturn(List.of());
        when(handoff.listForArtifact(any())).thenReturn(List.of());

        TicketTrackerService service = new TicketTrackerService(persistence, hermes, handoff);

        List<TicketTrackerView> pspTickets = service.listTickets("C:/Users/lingn/Inglab Project/");
        assertThat(pspTickets).extracting(TicketTrackerView::taskId).containsExactly("t1");

        List<TicketTrackerView> otherTickets = service.listTickets("C:/Users/lingn/SomeOtherProject");
        assertThat(otherTickets).isEmpty();

        assertThat(service.listTickets()).hasSize(2);
    }

    @Test
    void matchesSubfolderWorkspaceAgainstTicketTaggedWithRepoRoot() {
        ArtifactPersistenceService persistence = mock(ArtifactPersistenceService.class);
        HermesStatusService hermes = mock(HermesStatusService.class);
        ExternalHandoffService handoff = mock(ExternalHandoffService.class);

        when(persistence.listSummaries()).thenReturn(List.of(
                artifact("t1", "requirement-analysis", "2026-01-01T00:00:00Z", true, null, "C:/Users/lingn/Inglab Project")));
        when(hermes.currentForAllTasks()).thenReturn(List.of());
        when(handoff.listForArtifact(any())).thenReturn(List.of());

        TicketTrackerService service = new TicketTrackerService(persistence, hermes, handoff);

        List<TicketTrackerView> frontendWorkspace = service.listTickets("C:/Users/lingn/Inglab Project/pruserveplus-ipad");
        List<TicketTrackerView> unrelatedWorkspace = service.listTickets("C:/Users/lingn/Inglab ProjectX");

        assertThat(frontendWorkspace).extracting(TicketTrackerView::taskId).containsExactly("t1");
        assertThat(unrelatedWorkspace).isEmpty();
    }
}
