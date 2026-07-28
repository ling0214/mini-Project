package com.miniproject.backend.skills;

import com.miniproject.backend.mcp.ProjectGraphClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProjectContextMatcherTest {

    @TempDir
    Path repo;

    @Test
    void prefersCodebaseMemoryContextWhenAvailable() {
        ProjectGraphClient graphClient = mock(ProjectGraphClient.class);
        when(graphClient.searchProjectContext(eq("MyBanjirCare"), anyString(), anyInt()))
                .thenReturn(Map.of(
                        "project", "MyBanjirCare",
                        "source", "codebase-memory",
                        "matches", List.of(Map.of(
                                "found", true,
                                "name", "AidRequestController",
                                "file", "app/Http/Controllers/AidRequestController.php",
                                "line", 42,
                                "reason", "codebase-memory matched class AidRequestController as relevant to this ticket",
                                "source", "codebase-memory",
                                "affected", List.of()))));

        ProjectContextMatcher matcher = new ProjectContextMatcher("MyBanjirCare", repo, graphClient);

        List<Map<String, Object>> traces = matcher.findRelevantTraces(
                "Donor should filter aid request records by city and urgency.");

        assertThat(traces).hasSize(1);
        assertThat(traces.get(0).get("file")).isEqualTo("app/Http/Controllers/AidRequestController.php");
        assertThat(traces.get(0).get("source")).isEqualTo("codebase-memory");
        assertThat(traces.get(0).get("reason")).asString().contains("codebase-memory matched");
    }

    @Test
    void combinesCodebaseMemoryAndRepositoryEvidence() throws IOException {
        write("app/Http/Controllers/AidRequestController.php",
                "class AidRequestController { function index() { return city urgency category; } }");

        ProjectGraphClient graphClient = mock(ProjectGraphClient.class);
        when(graphClient.searchProjectContext(eq("MyBanjirCare"), anyString(), anyInt()))
                .thenReturn(Map.of(
                        "project", "MyBanjirCare",
                        "source", "codebase-memory",
                        "matches", List.of(Map.of(
                                "found", true,
                                "name", "aidRequest",
                                "file", "app/Models/Donation.php",
                                "line", 81,
                                "reason", "codebase-memory matched method aidRequest as relevant to this ticket",
                                "source", "codebase-memory",
                                "affected", List.of()))));

        ProjectContextMatcher matcher = new ProjectContextMatcher("MyBanjirCare", repo, graphClient);

        List<Map<String, Object>> traces = matcher.findRelevantTraces(
                "Donor should filter aid request records by city, category, and urgency.");

        assertThat(traces)
                .extracting(item -> item.get("file"))
                .contains(
                        "app/Models/Donation.php",
                        "app/Http/Controllers/AidRequestController.php");
    }

    @Test
    void retrievesRelevantLaravelFilesFromTargetRepository() throws IOException {
        write("routes/web.php", "Route::get('/donor/donations', [DonationController::class, 'index']);");
        write("app/Http/Controllers/DonationController.php", "class DonationController { function index() { return AidRequest::approved(); } }");
        write("app/Models/Donation.php", "class Donation { public function aidRequest() {} public function collectionCenter() {} }");
        write("app/Http/Controllers/AidRequestController.php", "class AidRequestController { function approved() { return city category urgency; } }");
        write("app/Models/AidRequest.php", "class AidRequest { protected $fillable = ['city_id', 'urgency_level', 'category']; }");
        write("app/Http/Controllers/Api/CityController.php", "class CityController { function search() {} }");
        write("resources/views/donor/donations/index.blade.php", "<select name='city'></select><select name='urgency'></select>");
        write("vendor/ignored.php", "donation aid request city urgency");

        ProjectContextMatcher matcher = new ProjectContextMatcher("MyBanjirCare", repo);

        List<Map<String, Object>> traces = matcher.findRelevantTraces(
                "Donor should be able to filter donation and aid request records by city and urgency.");

        assertThat(traces)
                .extracting(item -> item.get("file"))
                .contains(
                        "app/Http/Controllers/DonationController.php",
                        "app/Models/Donation.php",
                        "app/Http/Controllers/AidRequestController.php",
                        "app/Models/AidRequest.php",
                        "app/Http/Controllers/Api/CityController.php",
                        "routes/web.php")
                .doesNotContain("vendor/ignored.php");
        assertThat(traces)
                .extracting(item -> item.get("reason"))
                .anyMatch(reason -> String.valueOf(reason).contains("project context matched"));
    }

    @Test
    void fallsBackToDemoContextWhenRepositoryIsMissing() {
        ProjectContextMatcher matcher = new ProjectContextMatcher("MyBanjirCare", repo.resolve("missing"));

        List<Map<String, Object>> traces = matcher.findRelevantTraces(
                "Donor should filter aid request records by city and urgency.");

        assertThat(traces)
                .extracting(item -> item.get("file"))
                .contains(
                        "app/Http/Controllers/DonationController.php",
                        "app/Http/Controllers/AidRequestController.php",
                        "app/Http/Controllers/Api/CityController.php");
    }

    @Test
    void ignoresUnrelatedTicketsWhenNoRepositoryMatchExists() {
        ProjectContextMatcher matcher = new ProjectContextMatcher("MyBanjirCare", repo);

        List<Map<String, Object>> traces = matcher.findRelevantTraces(
                "Change the payment gateway retry policy.");

        assertThat(traces).isEmpty();
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = repo.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
