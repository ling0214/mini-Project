package com.miniproject.backend.integrations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Real file I/O against a throwaway temp "hermes home" (not mocks) -- this
 * exercises the exact same incidents/*.json + agent-tasks/* file layout that
 * Hermes's own plugin_api.py reads and mutates, so a passing test here is
 * genuine evidence the Java port behaves the same way, not just plausible.
 */
class HermesIncidentReaderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HermesIncidentReader reader = new HermesIncidentReader(objectMapper);

    @TempDir
    Path hermesHome;

    @Test
    void provisionHermesHomeCreatesTheExpectedSkeleton() {
        Path newProjectHome = hermesHome.resolve("brand-new-project");

        Map<String, Object> result = reader.provisionHermesHome(newProjectHome.toString());

        assertThat(result.get("already_existed")).isEqualTo(false);
        assertThat(Files.isDirectory(newProjectHome.resolve("incidents"))).isTrue();
        for (String folder : List.of("running", "pending", "completed", "failed")) {
            assertThat(Files.isDirectory(newProjectHome.resolve("agent-tasks").resolve(folder))).isTrue();
        }

        // Provisioning again is a safe no-op, not an error, and correctly reports it already existed.
        Map<String, Object> second = reader.provisionHermesHome(newProjectHome.toString());
        assertThat(second.get("already_existed")).isEqualTo(true);
    }

    @Test
    void listIncidentsReturnsEmptyWhenIncidentsDirIsMissing() {
        List<Map<String, Object>> incidents = reader.listIncidents(hermesHome.toString(), 50);

        assertThat(incidents).isEmpty();
    }

    @Test
    void listIncidentsComputesEffectiveStatusAndStaleness() throws IOException {
        writeIncident("fresh-incident", """
                {
                  "incident_key": "fresh-incident",
                  "stage": "log-analysis",
                  "status": "running",
                  "message": "Scanning logs",
                  "updated_at": "%s"
                }
                """.formatted(nowIso()));
        writeIncident("stale-incident", """
                {
                  "incident_key": "stale-incident",
                  "stage": "agent-dispatch",
                  "status": "running",
                  "message": "Dispatched to evidence agent",
                  "updated_at": "%s"
                }
                """.formatted(minutesAgoIso(45)));

        List<Map<String, Object>> incidents = reader.listIncidents(hermesHome.toString(), 50);

        Map<String, Object> fresh = incidents.stream().filter(i -> "fresh-incident".equals(i.get("incident_key"))).findFirst().orElseThrow();
        assertThat(fresh.get("effective_status")).isEqualTo("running");
        assertThat(fresh.get("is_stale")).isEqualTo(false);

        Map<String, Object> stale = incidents.stream().filter(i -> "stale-incident".equals(i.get("incident_key"))).findFirst().orElseThrow();
        assertThat(stale.get("effective_status")).isEqualTo("stale");
        assertThat(stale.get("is_stale")).isEqualTo(true);
    }

    @Test
    void getIncidentThrowsNotFoundForMissingKey() {
        assertThatThrownBy(() -> reader.getIncident(hermesHome.toString(), "does-not-exist"))
                .isInstanceOf(HermesIncidentReader.IncidentNotFoundException.class);
    }

    @Test
    void getIncidentRejectsPathTraversalInIncidentKey() throws IOException {
        writeIncident("real-incident", """
                {"incident_key": "real-incident", "status": "waiting", "updated_at": "%s"}
                """.formatted(nowIso()));
        Path secretOutsideIncidents = hermesHome.resolve("real-incident.json");
        Files.writeString(secretOutsideIncidents, "{\"should_not_be_read\": true}");

        assertThatThrownBy(() -> reader.getIncident(hermesHome.toString(), "../real-incident"))
                .isInstanceOf(HermesIncidentReader.IncidentNotFoundException.class);
    }

    @Test
    void stopIncidentAppendsHistorySetsFieldsAndFailsTheActiveTask() throws IOException {
        writeIncident("inc-1", """
                {
                  "incident_key": "inc-1",
                  "stage": "log-analysis",
                  "status": "running",
                  "message": "Scanning logs",
                  "updated_at": "%s",
                  "task_id": "task-abc",
                  "history": [{"at": "%s", "agent": "Log Fetch Agent", "stage": "log-lookup", "status": "completed", "message": "Log found"}]
                }
                """.formatted(nowIso(), nowIso()));
        Path runningTask = hermesHome.resolve("agent-tasks").resolve("running").resolve("task-abc.json");
        Files.createDirectories(runningTask.getParent());
        Files.writeString(runningTask, "{\"task_id\": \"task-abc\", \"status\": \"running\"}");

        Map<String, Object> result = reader.stopIncident(hermesHome.toString(), "inc-1");

        assertThat(result.get("status")).isEqualTo("stopped");
        assertThat(result.get("stop_requested")).isEqualTo(true);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history = (List<Map<String, Object>>) result.get("history");
        assertThat(history).hasSize(2);
        assertThat(history.get(1).get("message")).isEqualTo("Stop requested from Hermes UI");

        // The task-bus entry for this incident's task_id must move out of running/.
        assertThat(Files.exists(runningTask)).isFalse();
        Path failedTask = hermesHome.resolve("agent-tasks").resolve("failed").resolve("task-abc.json");
        assertThat(Files.exists(failedTask)).isTrue();
        JsonNode failedPayload = objectMapper.readTree(failedTask.toFile());
        assertThat(failedPayload.path("status").asText()).isEqualTo("failed");

        // And the on-disk incident file itself must reflect the same stopped state.
        JsonNode onDisk = objectMapper.readTree(hermesHome.resolve("incidents").resolve("inc-1.json").toFile());
        assertThat(onDisk.path("status").asText()).isEqualTo("stopped");
    }

    @Test
    void continueIncidentClearsStopAndSetsWaiting() throws IOException {
        writeIncident("inc-2", """
                {"incident_key": "inc-2", "status": "stopped", "stop_requested": true, "updated_at": "%s", "history": []}
                """.formatted(nowIso()));

        Map<String, Object> result = reader.continueIncident(hermesHome.toString(), "inc-2");

        assertThat(result.get("status")).isEqualTo("waiting");
        assertThat(result.get("stop_requested")).isEqualTo(false);
        assertThat(result.get("continue_requested")).isEqualTo(true);
    }

    @Test
    void retryIncidentIncrementsRetryCountAndMovesErrorToLastError() throws IOException {
        writeIncident("inc-3", """
                {"incident_key": "inc-3", "status": "failed", "error": "Python analyzer returned a failure", "retry_count": 1, "updated_at": "%s", "history": []}
                """.formatted(nowIso()));

        Map<String, Object> result = reader.retryIncident(hermesHome.toString(), "inc-3");

        assertThat(result.get("status")).isEqualTo("waiting");
        assertThat(result.get("retry_count")).isEqualTo(2);
        assertThat(result.get("last_error")).isEqualTo("Python analyzer returned a failure");
        assertThat(result.containsKey("error")).isFalse();
    }

    private void writeIncident(String incidentKey, String json) throws IOException {
        Path incidentsDir = hermesHome.resolve("incidents");
        Files.createDirectories(incidentsDir);
        Files.writeString(incidentsDir.resolve(incidentKey + ".json"), json);
    }

    private static String nowIso() {
        return OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private static String minutesAgoIso(int minutes) {
        return OffsetDateTime.now().minusMinutes(minutes).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
