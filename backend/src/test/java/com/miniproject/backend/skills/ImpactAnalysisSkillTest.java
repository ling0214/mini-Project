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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImpactAnalysisSkillTest {

    @TempDir
    Path repo;

    @Test
    void includesRetrievedProjectContextWhenGraphTraceDoesNotResolve() throws IOException {
        write("app/Http/Controllers/DonationController.php", "class DonationController { function index() { return AidRequest::approved(); } }");
        write("app/Models/Donation.php", "class Donation { public function aidRequest() {} }");
        write("app/Http/Controllers/AidRequestController.php", "class AidRequestController { function approved() { return city urgency; } }");
        write("app/Models/AidRequest.php", "class AidRequest { protected $fillable = ['city_id', 'urgency_level']; }");

        ProjectGraphClient graphClient = mock(ProjectGraphClient.class);
        when(graphClient.traceImpact(anyString(), anyInt())).thenAnswer(invocation -> {
            String name = invocation.getArgument(0, String.class);
            return Map.of("found", false, "name", name);
        });
        when(graphClient.searchIssues(anyString())).thenReturn(Map.of("query", "x", "matches", List.of(), "count", 0));

        ImpactAnalysisSkill skill = new ImpactAnalysisSkill(
                graphClient,
                new RuleBasedImpactAnalysisSynthesizer(),
                new ProjectContextMatcher("MyBanjirCare", repo));

        ImpactAnalysisResult result = skill.run(
                "Donor should be able to filter donation and aid request records by city and urgency.");

        assertThat(result.affectedModules())
                .extracting(ImpactAnalysisResult.AffectedModule::path)
                .contains(
                        "app/Http/Controllers/DonationController.php:1",
                        "app/Models/Donation.php:1",
                        "app/Http/Controllers/AidRequestController.php:1",
                        "app/Models/AidRequest.php:1");
        assertThat(result.affectedModules())
                .extracting(ImpactAnalysisResult.AffectedModule::reason)
                .anyMatch(reason -> reason.contains("MyBanjirCare project context matched"));
    }

    @Test
    void prefersTargetProjectContextOverHarnessGraphMatches() throws IOException {
        write("app/Http/Controllers/AidRequestController.php", "class AidRequestController { function index() { return city urgency category; } }");
        write("resources/views/aid-requests/index.blade.php", "donor aid request city urgency category filters");

        ProjectGraphClient graphClient = mock(ProjectGraphClient.class);
        when(graphClient.traceImpact(anyString(), anyInt())).thenReturn(Map.of(
                "found", true,
                "name", "description",
                "file", "backend/pom.xml",
                "line", 18,
                "affected", List.of()));
        when(graphClient.searchIssues(anyString())).thenReturn(Map.of(
                "query", "x",
                "matches", List.of(Map.of("id", 101, "state", "closed", "title", "Checkout fails when cart is empty")),
                "count", 1));

        ImpactAnalysisSkill skill = new ImpactAnalysisSkill(
                graphClient,
                new RuleBasedImpactAnalysisSynthesizer(),
                new ProjectContextMatcher("MyBanjirCare", repo));

        ImpactAnalysisResult result = skill.run(
                "Ticket title: Allow donors to filter available aid requests by city and urgency. "
                        + "Description: Donor should be able to filter approved aid request records by city, category, and urgency.");

        assertThat(result.affectedModules())
                .extracting(ImpactAnalysisResult.AffectedModule::path)
                .doesNotContain("backend/pom.xml:18")
                .contains("app/Http/Controllers/AidRequestController.php:1");
        assertThat(result.riskNotes()).isEmpty();
        assertThat(result.evidence())
                .extracting("claim")
                .noneMatch(claim -> String.valueOf(claim).contains("Checkout fails when cart is empty"));
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = repo.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
