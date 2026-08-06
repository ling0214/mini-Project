package com.miniproject.backend.integrations;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Reads/writes Hermes's own live incident-pipeline state -- the exact same
 * {hermes_home}/incidents/*.json files and {hermes_home}/agent-tasks/*
 * folders that Hermes's own "incident-dashboard" plugin
 * (plugins/incident-dashboard/dashboard/plugin_api.py in the hermes-agent
 * repo) reads and mutates. This is a second, independent Java port of that
 * FastAPI router rather than an HTTP client against it, because Hermes's own
 * dashboard server isn't guaranteed to be running -- the JSON files on disk
 * ARE the source of truth either way.
 *
 * hermesHome is the folder that directly contains "incidents" and
 * "agent-tasks" (e.g. C:/Users/you/AppData/Local/hermes), NOT the
 * hermes-agent repo checkout itself -- they are sibling folders.
 */
@Component
public class HermesIncidentReader {

    private static final double STALE_MINUTES_DEFAULT = 30.0;
    private static final int HISTORY_TAIL_FOR_LIST = 12;
    private static final int HISTORY_TAIL_FOR_WRITE = 100;

    private final ObjectMapper objectMapper;

    public HermesIncidentReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Best-effort auto-detect so the analyst never has to type or remember
     * this path -- Hermes itself always installs under %LOCALAPPDATA%/hermes
     * on Windows (confirmed against a real install: incidents/ and
     * agent-tasks/ both live directly under it), so this checks that
     * convention rather than guessing a hardcoded path. Returns null if
     * nothing matches -- the analyst can still type it manually.
     */
    public String detectHermesHome() {
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData == null || localAppData.isBlank()) {
            return null;
        }
        Path candidate = Path.of(localAppData, "hermes");
        boolean looksReal = Files.isDirectory(candidate.resolve("incidents"))
                || Files.isDirectory(candidate.resolve("agent-tasks"));
        return looksReal ? candidate.toString() : null;
    }

    /**
     * Creates an empty incidents/ + agent-tasks/{running,pending,completed,failed}
     * skeleton at the given path -- for onboarding a NEW project onto its own
     * separate Hermes install/profile (this repo's Hermes only ever serves
     * one project at a time; a second project needs its own instance pointed
     * at its own folder, same shape Hermes itself already expects). This
     * only ever creates empty directories -- it never touches Hermes's own
     * incident-creation code, so it can't affect any live incident.
     */
    public Map<String, Object> provisionHermesHome(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        Path home = Path.of(path.trim());
        boolean alreadyExisted = Files.isDirectory(home.resolve("incidents")) || Files.isDirectory(home.resolve("agent-tasks"));
        try {
            Files.createDirectories(home.resolve("incidents"));
            for (String folder : new String[] {"running", "pending", "completed", "failed"}) {
                Files.createDirectories(home.resolve("agent-tasks").resolve(folder));
            }
        } catch (IOException e) {
            throw new HermesIncidentException("Could not create folders at " + home + ": " + e.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hermes_home", home.toString());
        result.put("already_existed", alreadyExisted);
        return result;
    }

    public List<Map<String, Object>> listIncidents(String hermesHome, int limit) {
        Path dir = incidentDir(hermesHome);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        int maxRows = Math.max(1, Math.min(limit <= 0 ? 50 : limit, 200));

        List<Path> files;
        try (Stream<Path> stream = Files.list(dir)) {
            files = stream
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparingLong(this::lastModifiedMillis).reversed())
                    .toList();
        } catch (IOException e) {
            throw new HermesIncidentException("Could not list " + dir + ": " + e.getMessage());
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Path file : files) {
            JsonNode payload = readJsonQuiet(file);
            if (payload == null) {
                continue;
            }
            rows.add(summarize(payload, file));
            if (rows.size() >= maxRows) {
                break;
            }
        }
        return rows;
    }

    public Map<String, Object> getIncident(String hermesHome, String incidentKey) {
        Path file = incidentFile(hermesHome, incidentKey);
        JsonNode payload = requireIncident(file, incidentKey);
        Map<String, Object> result = toMap(payload);
        boolean stale = isStaleRunning(payload);
        result.put("effective_status", stale ? "stale" : textOrNull(payload, "status"));
        result.put("is_stale", stale);
        result.put("age_minutes", ageMinutes(payload));
        return result;
    }

    public Map<String, Object> stopIncident(String hermesHome, String incidentKey) {
        Path file = incidentFile(hermesHome, incidentKey);
        ObjectNode payload = (ObjectNode) requireIncident(file, incidentKey);

        String now = nowIso();
        appendHistory(payload, now, "Hermes UI", "incident-control", "stopped", "Stop requested from Hermes UI");
        payload.put("incident_key", textOr(payload, "incident_key", safeKey(incidentKey)));
        payload.put("current_agent", "Hermes UI");
        payload.put("stage", "incident-control");
        payload.put("status", "stopped");
        payload.put("message", "Stop requested from Hermes UI");
        payload.put("updated_at", now);
        payload.put("stop_requested", true);
        payload.put("stopped_at", now);

        writeJson(file, payload);
        failActiveTaskForIncident(hermesHome, payload, "Stopped from Hermes UI");
        return toMap(payload);
    }

    public Map<String, Object> continueIncident(String hermesHome, String incidentKey) {
        Path file = incidentFile(hermesHome, incidentKey);
        ObjectNode payload = (ObjectNode) requireIncident(file, incidentKey);

        String now = nowIso();
        appendHistory(payload, now, "Hermes UI", "incident-control", "waiting", "Continue requested from Hermes UI");
        payload.put("incident_key", textOr(payload, "incident_key", safeKey(incidentKey)));
        payload.put("current_agent", "Hermes UI");
        payload.put("stage", "incident-control");
        payload.put("status", "waiting");
        payload.put("message", "Continue requested from Hermes UI");
        payload.put("updated_at", now);
        payload.put("stop_requested", false);
        payload.put("continue_requested", true);
        payload.put("continued_at", now);

        writeJson(file, payload);
        return toMap(payload);
    }

    public Map<String, Object> retryIncident(String hermesHome, String incidentKey) {
        Path file = incidentFile(hermesHome, incidentKey);
        ObjectNode payload = (ObjectNode) requireIncident(file, incidentKey);

        String now = nowIso();
        appendHistory(payload, now, "Hermes UI", "incident-control", "waiting", "Retry requested from Hermes UI");

        int retryCount;
        try {
            retryCount = (payload.hasNonNull("retry_count") ? payload.get("retry_count").asInt(0) : 0) + 1;
        } catch (Exception e) {
            retryCount = 1;
        }
        String previousError = payload.hasNonNull("error") ? payload.get("error").asText() : null;

        payload.put("incident_key", textOr(payload, "incident_key", safeKey(incidentKey)));
        payload.put("current_agent", "Hermes UI");
        payload.put("stage", "incident-control");
        payload.put("status", "waiting");
        payload.put("message", "Retry requested from Hermes UI");
        payload.put("updated_at", now);
        payload.put("stop_requested", false);
        payload.put("continue_requested", true);
        payload.put("retry_requested", true);
        payload.put("retry_from_stage", "log-analysis");
        payload.put("retry_count", retryCount);
        payload.put("retried_at", now);
        payload.put("continued_at", now);
        if (previousError != null) {
            payload.put("last_error", previousError);
        }
        payload.remove("error");

        writeJson(file, payload);
        failActiveTaskForIncident(hermesHome, payload, "Retry requested from Hermes UI");
        return toMap(payload);
    }

    // -- paths --------------------------------------------------------

    private Path hermesHomeDir(String hermesHome) {
        if (hermesHome == null || hermesHome.isBlank()) {
            throw new IllegalArgumentException("hermesHome is required (the folder containing incidents/ and agent-tasks/)");
        }
        return Path.of(hermesHome.trim());
    }

    private Path incidentDir(String hermesHome) {
        return hermesHomeDir(hermesHome).resolve("incidents");
    }

    private Path agentTasksDir(String hermesHome) {
        return hermesHomeDir(hermesHome).resolve("agent-tasks");
    }

    private Path incidentFile(String hermesHome, String incidentKey) {
        return incidentDir(hermesHome).resolve(safeKey(incidentKey) + ".json");
    }

    /** Same allow-list as Hermes's own _safe_key -- incidentKey reaches here from a URL path segment, so this is the path-traversal guard, not cosmetic. */
    private static String safeKey(String key) {
        if (key == null) {
            return "";
        }
        StringBuilder cleaned = new StringBuilder();
        for (char c : key.toCharArray()) {
            if (Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == '.') {
                cleaned.append(c);
            }
        }
        return cleaned.length() > 180 ? cleaned.substring(0, 180) : cleaned.toString();
    }

    // -- reading --------------------------------------------------------

    private JsonNode requireIncident(Path file, String incidentKey) {
        if (!Files.isRegularFile(file)) {
            throw new IncidentNotFoundException(incidentKey);
        }
        JsonNode payload = readJsonQuiet(file);
        if (payload == null) {
            throw new HermesIncidentException("Incident JSON is unreadable: " + incidentKey);
        }
        return payload;
    }

    private JsonNode readJsonQuiet(Path file) {
        try {
            return objectMapper.readTree(file.toFile());
        } catch (IOException e) {
            return null;
        }
    }

    private long lastModifiedMillis(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private Map<String, Object> toMap(JsonNode node) {
        return objectMapper.convertValue(node, new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }

    private Map<String, Object> summarize(JsonNode payload, Path file) {
        boolean stale = isStaleRunning(payload);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("incident_key", textOr(payload, "incident_key", stripJsonExt(file.getFileName().toString())));
        row.put("stage", textOrNull(payload, "stage"));
        row.put("status", textOrNull(payload, "status"));
        row.put("effective_status", stale ? "stale" : textOrNull(payload, "status"));
        row.put("is_stale", stale);
        row.put("age_minutes", ageMinutes(payload));
        row.put("message", textOrNull(payload, "message"));
        row.put("error", textOrNull(payload, "error"));
        row.put("current_agent", textOrNull(payload, "current_agent"));
        row.put("current_log", textOrNull(payload, "current_log"));
        row.put("report_path", textOrNull(payload, "report_path"));
        row.put("json_report_path", textOrNull(payload, "json_report_path"));
        row.put("rca_code_report", textOrNull(payload, "rca_code_report"));
        row.put("target_agent", textOrNull(payload, "target_agent"));
        row.put("task_id", textOrNull(payload, "task_id"));
        row.put("task_type", textOrNull(payload, "task_type"));
        row.put("updated_at", textOrNull(payload, "updated_at"));
        row.put("thread_id", textOrNull(payload, "thread_id"));
        row.put("stop_requested", boolOrNull(payload, "stop_requested"));
        row.put("continue_requested", boolOrNull(payload, "continue_requested"));
        row.put("retry_requested", boolOrNull(payload, "retry_requested"));
        row.put("retry_count", intOrNull(payload, "retry_count"));
        row.put("retried_at", textOrNull(payload, "retried_at"));
        row.put("stopped_at", textOrNull(payload, "stopped_at"));
        row.put("continued_at", textOrNull(payload, "continued_at"));
        row.put("history", historyTail(payload, HISTORY_TAIL_FOR_LIST));
        return row;
    }

    private List<Map<String, Object>> historyTail(JsonNode payload, int tail) {
        JsonNode history = payload.path("history");
        if (!history.isArray()) {
            return List.of();
        }
        List<Map<String, Object>> all = new ArrayList<>();
        for (JsonNode item : history) {
            all.add(toMap(item));
        }
        int from = Math.max(0, all.size() - tail);
        return all.subList(from, all.size());
    }

    private static String stripJsonExt(String filename) {
        return filename.endsWith(".json") ? filename.substring(0, filename.length() - 5) : filename;
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private String textOr(JsonNode node, String field, String fallback) {
        String value = textOrNull(node, field);
        return value == null || value.isBlank() ? fallback : value;
    }

    private Boolean boolOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asBoolean();
    }

    private Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asInt();
    }

    // -- timing / staleness --------------------------------------------------------

    private Double ageMinutes(JsonNode payload) {
        String updatedAt = textOrNull(payload, "updated_at");
        if (updatedAt == null) {
            return null;
        }
        try {
            OffsetDateTime updated = OffsetDateTime.parse(updatedAt);
            Duration elapsed = Duration.between(updated, OffsetDateTime.now(updated.getOffset()));
            return Math.max(0.0, elapsed.toMillis() / 60000.0);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isStaleRunning(JsonNode payload) {
        String status = textOrNull(payload, "status");
        if (status == null || !status.equalsIgnoreCase("running")) {
            return false;
        }
        Double age = ageMinutes(payload);
        return age != null && age >= STALE_MINUTES_DEFAULT;
    }

    private String nowIso() {
        return OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    // -- writing / mutation --------------------------------------------------------

    private void appendHistory(ObjectNode payload, String at, String agent, String stage, String status, String message) {
        ArrayNode history = payload.has("history") && payload.get("history").isArray()
                ? (ArrayNode) payload.get("history")
                : objectMapper.createArrayNode();
        ObjectNode entry = objectMapper.createObjectNode();
        entry.put("at", at);
        entry.put("agent", agent);
        entry.put("stage", stage);
        entry.put("status", status);
        entry.put("message", message);
        history.add(entry);
        while (history.size() > HISTORY_TAIL_FOR_WRITE) {
            history.remove(0);
        }
        payload.set("history", history);
    }

    private void writeJson(Path file, Object payload) {
        try {
            Files.createDirectories(file.getParent());
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), payload);
        } catch (IOException e) {
            throw new HermesIncidentException("Could not write " + file + ": " + e.getMessage());
        }
    }

    /**
     * The task-bus (a separate Hermes subsystem) is filesystem-as-state-machine:
     * a task's state IS which of pending/running/completed/failed its JSON file
     * lives in. Stopping/retrying an incident here only ever touched the
     * incident-status JSON above -- without this, a dispatched-but-unfinished
     * task is left "running" forever in the task-bus and every later resume
     * just waits on it. Mirrors plugin_api.py's _fail_active_task_for_incident
     * exactly (including which folders it checks and in what order).
     */
    private void failActiveTaskForIncident(String hermesHome, JsonNode payload, String note) {
        String taskId = textOrNull(payload, "task_id");
        if (taskId == null || taskId.isBlank()) {
            return;
        }
        String safeTaskId = safeKey(taskId);
        Path taskRoot = agentTasksDir(hermesHome);
        for (String folder : new String[] {"running", "pending"}) {
            Path taskPath = taskRoot.resolve(folder).resolve(safeTaskId + ".json");
            if (!Files.isRegularFile(taskPath)) {
                continue;
            }
            JsonNode taskPayload = readJsonQuiet(taskPath);
            if (taskPayload == null || !taskPayload.isObject()) {
                continue;
            }
            ObjectNode taskNode = (ObjectNode) taskPayload;
            taskNode.put("status", "failed");
            taskNode.put("failed_at", OffsetDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            taskNode.put("error", note);
            writeJson(taskRoot.resolve("failed").resolve(taskPath.getFileName()), taskNode);
            try {
                Files.deleteIfExists(taskPath);
            } catch (IOException ignored) {
                // best-effort, matches plugin_api.py's bare `except OSError: pass`
            }
            return;
        }
    }

    public static class HermesIncidentException extends RuntimeException {
        public HermesIncidentException(String message) {
            super(message);
        }
    }

    public static class IncidentNotFoundException extends RuntimeException {
        public IncidentNotFoundException(String incidentKey) {
            super("Incident not found: " + incidentKey);
        }
    }
}
