package com.miniproject.backend.skills;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectContextMatcherTest {

    @TempDir
    Path repo;

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
