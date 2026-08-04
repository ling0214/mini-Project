package com.miniproject.backend.tracker;

import com.miniproject.backend.integrations.ExternalHandoffService;
import com.miniproject.backend.integrations.HermesStatusService;
import com.miniproject.backend.integrations.HermesStatusView;
import com.miniproject.backend.persistence.ArtifactPersistenceService;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Groups persisted artifacts into per-ticket chains (root requirement-analysis
 * + everything reachable via parentTaskId: clarify rounds, handoffs) and
 * derives each ticket's 6-phase delivery status from real data -- see the
 * conversation spec: Impact Analysis is "skipped" (not "pending") once a
 * ticket already has a Hermes status but never got its own impact-analysis
 * artifact, since that means the analyst routed it straight to Hermes for RCA.
 */
@Service
public class TicketTrackerService {

    private static final List<String> PHASE_NAMES = List.of(
            "Requirement Review (ticket raised)",
            "Impact Analysis",
            "Development / Fixing",
            "Testing",
            "Review / Handoff",
            "Jira / UI Sync");

    /** Ordinal progression mirroring HermesStatusService.VALID_STATUSES minus "Sent to Hermes". */
    private static final List<String> HERMES_STAGE_ORDER = List.of(
            "Hermes accepted", "Developer update", "Testing decision", "Close summary");

    // inputPreview is the collapsed-to-one-line, 140-char-truncated
    // analysisInput() text (see RequirementAnalysisRequest / ArtifactPersistenceService.truncate) --
    // pull the "Title: ..." field back out for a readable tab label instead
    // of showing the raw concatenated field dump.
    private static final Pattern TITLE_FIELD = Pattern.compile(
            "Title:\\s*(.+?)(?=\\s+(?:Ticket key|Priority|Reporter|Source type|Source name|Source URL|Received|Description|Acceptance criteria|Comments / notes|Code evidence):|$)");
    private static final Pattern TICKET_KEY_FIELD = Pattern.compile("Ticket key:\\s*(\\S+)");
    private static final int FALLBACK_TITLE_LENGTH = 70;

    private final ArtifactPersistenceService persistence;
    private final HermesStatusService hermesStatusService;
    private final ExternalHandoffService externalHandoffService;

    public TicketTrackerService(
            ArtifactPersistenceService persistence,
            HermesStatusService hermesStatusService,
            ExternalHandoffService externalHandoffService) {
        this.persistence = persistence;
        this.hermesStatusService = hermesStatusService;
        this.externalHandoffService = externalHandoffService;
    }

    public List<TicketTrackerView> listTickets() {
        return listTickets(null);
    }

    /**
     * project is a workspace's local_path, not its display name -- same
     * rationale as HermesStatusService.normalizeProject: names are free text
     * an analyst can rename anytime, path is the stable value. Pass null/blank
     * for the unfiltered (every connected project's tickets) view.
     */
    public List<TicketTrackerView> listTickets(String project) {
        String cleanedProject = normalizeProject(project);
        List<ArtifactPersistenceService.ArtifactSummary> all = persistence.listSummaries();

        Map<String, List<ArtifactPersistenceService.ArtifactSummary>> childrenByParent = all.stream()
                .filter(s -> s.parentTaskId() != null && !s.parentTaskId().isBlank())
                .collect(Collectors.groupingBy(ArtifactPersistenceService.ArtifactSummary::parentTaskId));

        List<ArtifactPersistenceService.ArtifactSummary> roots = all.stream()
                .filter(s -> "requirement-analysis".equals(s.skill()))
                .filter(s -> s.parentTaskId() == null || s.parentTaskId().isBlank())
                .filter(s -> cleanedProject == null || pathsRelated(cleanedProject, s.projectPath()))
                .sorted(Comparator.comparing(ArtifactPersistenceService.ArtifactSummary::createdAt).reversed())
                .toList();

        Map<String, HermesStatusView> currentHermesByTask = hermesStatusService.currentForAllTasks().stream()
                .collect(Collectors.toMap(HermesStatusView::sourceTaskId, v -> v, (a, b) -> a));

        List<TicketTrackerView> result = new ArrayList<>();
        for (ArtifactPersistenceService.ArtifactSummary root : roots) {
            List<ArtifactPersistenceService.ArtifactSummary> chain = collectChain(root, childrenByParent);
            result.add(buildTicketView(root, chain, currentHermesByTask));
        }
        return result;
    }

    private List<ArtifactPersistenceService.ArtifactSummary> collectChain(
            ArtifactPersistenceService.ArtifactSummary root,
            Map<String, List<ArtifactPersistenceService.ArtifactSummary>> childrenByParent) {
        List<ArtifactPersistenceService.ArtifactSummary> chain = new ArrayList<>();
        Deque<ArtifactPersistenceService.ArtifactSummary> stack = new ArrayDeque<>();
        stack.push(root);
        while (!stack.isEmpty()) {
            ArtifactPersistenceService.ArtifactSummary current = stack.pop();
            chain.add(current);
            for (ArtifactPersistenceService.ArtifactSummary child : childrenByParent.getOrDefault(current.taskId(), List.of())) {
                stack.push(child);
            }
        }
        return chain;
    }

    private TicketTrackerView buildTicketView(
            ArtifactPersistenceService.ArtifactSummary root,
            List<ArtifactPersistenceService.ArtifactSummary> chain,
            Map<String, HermesStatusView> currentHermesByTask) {

        boolean reqReviewDone = chain.stream()
                .filter(s -> "requirement-analysis".equals(s.skill()))
                .max(Comparator.comparing(ArtifactPersistenceService.ArtifactSummary::createdAt))
                .map(ArtifactPersistenceService.ArtifactSummary::reviewed)
                .orElse(false);

        boolean impactDone = chain.stream()
                .anyMatch(s -> "impact-analysis".equals(s.skill()) && s.reviewed());

        int hermesStage = chain.stream()
                .map(s -> currentHermesByTask.get(s.taskId()))
                .filter(Objects::nonNull)
                .mapToInt(v -> HERMES_STAGE_ORDER.indexOf(v.status()))
                .max()
                .orElse(-1);

        boolean impactSkipped = !impactDone && hermesStage >= 0;

        boolean devDone = hermesStage >= 2;

        boolean testDone = hermesStage >= 3
                || chain.stream().anyMatch(s ->
                        ("test-case-gen".equals(s.skill()) || "test-scope-review".equals(s.skill())) && s.reviewed());

        boolean reviewHandoffDone = chain.stream()
                .anyMatch(s -> "handoff-summary".equals(s.skill()) && s.reviewed());

        boolean jiraSyncDone = chain.stream()
                .flatMap(s -> externalHandoffService.listForArtifact(s.taskId()).stream())
                .anyMatch(h -> ("jira".equals(h.destination()) || "jira-comment".equals(h.destination()))
                        && !h.dryRun()
                        && ("CREATED".equals(h.status()) || "COMMENTED".equals(h.status())));

        boolean[] done = {reqReviewDone, impactDone, devDone, testDone, reviewHandoffDone, jiraSyncDone};

        List<TicketPhaseView> phases = new ArrayList<>();
        boolean cursorSet = false;
        for (int i = 0; i < PHASE_NAMES.size(); i++) {
            String state;
            if (done[i]) {
                state = "done";
            } else if (i == 1 && impactSkipped) {
                state = "skipped";
            } else if (!cursorSet) {
                state = "active";
                cursorSet = true;
            } else {
                state = "pending";
            }
            phases.add(new TicketPhaseView(PHASE_NAMES.get(i), state));
        }

        String ticketType = impactSkipped ? "issue" : "change_request";
        // ISO-8601 timestamps sort correctly as plain strings; the most
        // recent createdAt in the chain is the last thing that actually
        // happened to this ticket (clarify round, handoff, etc.) since
        // artifacts are immutable once written.
        String updatedAt = chain.stream()
                .map(ArtifactPersistenceService.ArtifactSummary::createdAt)
                .max(Comparator.naturalOrder())
                .orElse(root.createdAt());
        return new TicketTrackerView(root.taskId(), titleFor(root.inputPreview()), ticketType, phases, updatedAt);
    }

    private static String normalizeProject(String project) {
        if (project == null) {
            return null;
        }
        String trimmed = project.trim();
        while (trimmed.endsWith("/") || trimmed.endsWith("\\")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isBlank() ? null : trimmed;
    }

    /**
     * True when the two paths are the same project, or one is a subfolder of
     * the other -- mirrors HermesStatusService.pathsRelated. mini-Project may
     * declare "PSP Frontend" / "PSP Backend" as separate workspaces (each a
     * subfolder of the same repo root) while a ticket's project_path was
     * captured from whichever one was active at creation time.
     */
    private static boolean pathsRelated(String a, String b) {
        String na = normalizeProject(a);
        String nb = normalizeProject(b);
        if (na == null || nb == null) {
            return false;
        }
        String la = na.toLowerCase(Locale.ROOT);
        String lb = nb.toLowerCase(Locale.ROOT);
        if (la.equals(lb)) {
            return true;
        }
        return la.startsWith(lb + "/") || la.startsWith(lb + "\\")
                || lb.startsWith(la + "/") || lb.startsWith(la + "\\");
    }

    private static String titleFor(String inputPreview) {
        String preview = inputPreview == null ? "" : inputPreview;

        Matcher titleMatch = TITLE_FIELD.matcher(preview);
        if (titleMatch.find() && !titleMatch.group(1).isBlank()) {
            return titleMatch.group(1).trim();
        }

        Matcher ticketKeyMatch = TICKET_KEY_FIELD.matcher(preview);
        if (ticketKeyMatch.find()) {
            return "Ticket " + ticketKeyMatch.group(1);
        }

        return preview.length() > FALLBACK_TITLE_LENGTH
                ? preview.substring(0, FALLBACK_TITLE_LENGTH) + "…"
                : preview;
    }
}
