package com.miniproject.backend.integrations;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class HermesStatusService {

    /**
     * Fixed vocabulary matching the Hermes handoff timeline shown in the
     * frontend (main.jsx HERMES_HANDOFF_STEPS) — keep both lists in sync if
     * these labels ever change.
     */
    public static final List<String> VALID_STATUSES = List.of(
            "Sent to Hermes", "Hermes accepted", "Developer update", "Testing decision", "Close summary");

    private static final Set<String> VALID_STATUS_SET = new LinkedHashSet<>(VALID_STATUSES);

    private final HermesStatusRepository repository;

    public HermesStatusService(HermesStatusRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public HermesStatusView recordStatus(
            String sourceTaskId, String status, String note, String project, String similarIssues) {
        if (sourceTaskId == null || sourceTaskId.isBlank()) {
            throw new IllegalArgumentException("source_task_id is required");
        }
        String cleanedStatus = status == null ? "" : status.trim();
        if (!VALID_STATUS_SET.contains(cleanedStatus)) {
            throw new IllegalArgumentException("status must be one of " + VALID_STATUSES + ", got: " + status);
        }

        Optional<HermesStatusEntity> previous = repository.findBySourceTaskIdAndDeleteDateIsNull(sourceTaskId.trim());
        previous.ifPresent(HermesStatusEntity::markSuperseded);

        // A status report that doesn't repeat the project (e.g. a later
        // Hermes PR-package callback) inherits whichever project was
        // established when this task was first tracked, so the tracker can
        // still scope it to the right connected project.
        String cleanedProject = normalizeProject(project);
        if (cleanedProject == null) {
            cleanedProject = previous.map(HermesStatusEntity::getProject).orElse(null);
        }

        // Same carry-forward as project -- the similar-issue-check RAG
        // result only fires once early in the incident's life, later status
        // reports (Developer update, Testing decision, ...) don't repeat it
        // but the analyst should still see it once it's known.
        String cleanedSimilarIssues = blankToNull(similarIssues);
        if (cleanedSimilarIssues == null) {
            cleanedSimilarIssues = previous.map(HermesStatusEntity::getSimilarIssues).orElse(null);
        }

        HermesStatusEntity saved = repository.save(
                new HermesStatusEntity(sourceTaskId.trim(), cleanedStatus, note, cleanedProject, cleanedSimilarIssues));
        return saved.toView();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * project is a workspace's local_path (e.g. "C:/Users/lingn/Inglab Project"),
     * not its display name — names are free text the analyst can rename anytime
     * ("PSP" -> "psp testing"), so path is the only stable, independently
     * derivable value both mini-Project and Hermes can agree on without staying
     * in string-sync. Trim trailing slashes and compare case-insensitively
     * (Windows paths) at the repository layer.
     */
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
     * the other -- e.g. mini-Project may declare "PSP Frontend"
     * (.../Inglab Project/pruserveplus-ipad) and "PSP Backend"
     * (.../Inglab Project/pruserve-backoffice) as separate workspaces while
     * Hermes tags every status with the whole repo root (.../Inglab Project).
     * Requires a path-separator boundary right after the shorter prefix so
     * ".../InglabProjectX" never falsely matches ".../InglabProject".
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

    @Transactional(readOnly = true)
    public List<HermesStatusView> history(String sourceTaskId) {
        return repository.findBySourceTaskIdOrderByCreateDateDesc(sourceTaskId).stream()
                .map(HermesStatusEntity::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<HermesStatusView> current(String sourceTaskId) {
        return repository.findBySourceTaskIdAndDeleteDateIsNull(sourceTaskId).map(HermesStatusEntity::toView);
    }

    @Transactional(readOnly = true)
    public List<HermesStatusView> currentForAllTasks() {
        return currentForAllTasks(null);
    }

    @Transactional(readOnly = true)
    public List<HermesStatusView> currentForAllTasks(String project) {
        String cleanedProject = normalizeProject(project);
        List<HermesStatusEntity> entities = repository.findByDeleteDateIsNullOrderByCreateDateDesc();
        return entities.stream()
                .filter(e -> cleanedProject == null || pathsRelated(cleanedProject, e.getProject()))
                .map(HermesStatusEntity::toView)
                .toList();
    }
}
