package com.miniproject.backend.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EndpointSequenceDiagramServiceTest {

    private final EndpointSequenceDiagramService service = new EndpointSequenceDiagramService();

    @Test
    void listsLaravelRoutesAndGeneratesEndpointSequence(@TempDir Path project) throws Exception {
        Files.createDirectories(project.resolve("routes"));
        Files.createDirectories(project.resolve("app/Http/Controllers"));
        Files.createDirectories(project.resolve("app/Models"));
        Files.writeString(project.resolve("routes/web.php"), """
                <?php
                use App\\Http\\Controllers\\AidRequestController;
                Route::get('/aid-requests/approved/api', [AidRequestController::class, 'approved'])->name('aid.approved');
                """);
        Files.writeString(project.resolve("app/Models/AidRequest.php"), "<?php class AidRequest {}");
        Files.writeString(project.resolve("app/Http/Controllers/AidRequestController.php"), """
                <?php
                class AidRequestController {
                    public function approved()
                    {
                        $items = AidRequest::where('status', 'approved')->get();
                        return response()->json($items);
                    }
                }
                """);

        List<EndpointSequenceDiagramService.EndpointOption> endpoints = service.listEndpoints(project);

        assertThat(endpoints).hasSize(1);
        assertThat(endpoints.get(0).method()).isEqualTo("GET");
        assertThat(endpoints.get(0).path()).isEqualTo("/aid-requests/approved/api");

        String mermaid = service.generateMermaid(project, endpoints.get(0).id());

        assertThat(mermaid).startsWith("sequenceDiagram");
        assertThat(mermaid).contains("Laravel Route");
        assertThat(mermaid).contains("AidRequestController.approved()");
        assertThat(mermaid).contains("AidRequest model");
        assertThat(mermaid).contains("Database");
        assertThat(mermaid).contains("View/JSON response");
    }

    @Test
    void listsSpringRoutesAndGeneratesEndpointSequence(@TempDir Path project) throws Exception {
        Path controllerDir = project.resolve("src/main/java/com/example/web");
        Files.createDirectories(controllerDir);
        Files.writeString(controllerDir.resolve("ProjectController.java"), """
                package com.example.web;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequestMapping("/api/projects")
                public class ProjectController {
                    @GetMapping("/{id}")
                    public String show() {
                        return repository.findById("demo").orElseThrow();
                    }
                }
                """);

        List<EndpointSequenceDiagramService.EndpointOption> endpoints = service.listEndpoints(project);

        assertThat(endpoints).hasSize(1);
        assertThat(endpoints.get(0).framework()).isEqualTo("spring");
        assertThat(endpoints.get(0).method()).isEqualTo("GET");
        assertThat(endpoints.get(0).path()).isEqualTo("/api/projects/{id}");

        String mermaid = service.generateMermaid(project, endpoints.get(0).id());

        assertThat(mermaid).startsWith("sequenceDiagram");
        assertThat(mermaid).contains("Spring MVC Route");
        assertThat(mermaid).contains("ProjectController.show()");
        assertThat(mermaid).contains("Database");
    }

    @Test
    void listsFrontendApiCallsAndGeneratesFrontendSequence(@TempDir Path project) throws Exception {
        Files.createDirectories(project.resolve("frontend/src"));
        Files.writeString(project.resolve("frontend/package.json"), "{\"scripts\":{\"dev\":\"vite\"}}");
        Files.writeString(project.resolve("frontend/src/main.jsx"), """
                function AnalystInboxPhase() {
                  async function importJiraTicket() {
                    return api("/api/integrations/jira/import", {
                      method: "POST",
                      body: { issueKey: "KAN-1" }
                    });
                  }

                  async function loadArtifact(taskId) {
                    return api(`/api/artifacts/${taskId}`);
                  }
                }
                """);

        List<EndpointSequenceDiagramService.EndpointOption> endpoints = service.listEndpoints(project);

        assertThat(endpoints)
                .extracting(EndpointSequenceDiagramService.EndpointOption::framework)
                .containsOnly("frontend");
        assertThat(endpoints)
                .extracting(EndpointSequenceDiagramService.EndpointOption::path)
                .contains("/api/integrations/jira/import", "/api/artifacts/{value}");
        assertThat(endpoints)
                .anySatisfy(endpoint -> {
                    assertThat(endpoint.method()).isEqualTo("POST");
                    assertThat(endpoint.controller()).isEqualTo("AnalystInboxPhase");
                    assertThat(endpoint.action()).isEqualTo("importJiraTicket");
                });

        EndpointSequenceDiagramService.EndpointOption postEndpoint = endpoints.stream()
                .filter(endpoint -> endpoint.path().equals("/api/integrations/jira/import"))
                .findFirst()
                .orElseThrow();
        String mermaid = service.generateMermaid(project, postEndpoint.id(), "graphify");

        assertThat(mermaid).startsWith("sequenceDiagram");
        assertThat(mermaid).contains("React UI");
        assertThat(mermaid).contains("frontend api() helper");
        assertThat(mermaid).contains("POST /api/integrations/jira/import");
        assertThat(mermaid).contains("AnalystInboxPhase.importJiraTicket()");
    }

    @Test
    void generatesGraphifyBackedEndpointSequence(@TempDir Path project) throws Exception {
        Files.createDirectories(project.resolve("routes"));
        Files.createDirectories(project.resolve("app/Http/Controllers"));
        Files.createDirectories(project.resolve("graphify-out"));
        Files.writeString(project.resolve("routes/web.php"), """
                <?php
                Route::post('/flood-report', [App\\Http\\Controllers\\FloodReportController::class, 'store'])->name('flood-report.store');
                """);
        Files.writeString(project.resolve("app/Http/Controllers/FloodReportController.php"), """
                <?php
                class FloodReportController {
                    public function store() {
                        return response()->json([]);
                    }
                }
                """);
        Files.writeString(project.resolve("graphify-out/graph.json"), """
                {
                  "nodes": [
                    {
                      "id": "app_http_controllers_floodreportcontroller_floodreportcontroller_store",
                      "label": ".store()",
                      "source_file": "app/Http/Controllers/FloodReportController.php",
                      "source_location": "L14"
                    },
                    {
                      "id": "app_models_floodreport_floodreport",
                      "label": "FloodReport",
                      "source_file": "app/Models/FloodReport.php",
                      "source_location": "L8"
                    }
                  ],
                  "links": [
                    {
                      "relation": "calls",
                      "confidence": "EXTRACTED",
                      "source_file": "app/Http/Controllers/FloodReportController.php",
                      "source_location": "L58",
                      "source": "app_http_controllers_floodreportcontroller_floodreportcontroller_store",
                      "target": "app_models_floodreport_floodreport"
                    }
                  ]
                }
                """);

        List<EndpointSequenceDiagramService.EndpointOption> endpoints = service.listEndpoints(project);

        String mermaid = service.generateMermaid(project, endpoints.get(0).id(), "graphify");

        assertThat(mermaid).startsWith("sequenceDiagram");
        assertThat(mermaid).contains("Graphify node app/Http/Controllers/FloodReportController.php L14");
        assertThat(mermaid).contains("FloodReport model");
        assertThat(mermaid).contains("EXTRACTED calls at app/Http/Controllers/FloodReportController.php L58");
    }

    @Test
    void generatesGraphifyBackedSpringEndpointSequence(@TempDir Path project) throws Exception {
        Files.createDirectories(project.resolve("src/main/java/com/example/web"));
        Files.createDirectories(project.resolve("graphify-out"));
        Files.writeString(project.resolve("src/main/java/com/example/web/ProjectController.java"), """
                package com.example.web;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                class ProjectController {
                    @GetMapping("/projects")
                    public String list() {
                        return "ok";
                    }
                }
                """);
        Files.writeString(project.resolve("graphify-out/graph.json"), """
                {
                  "nodes": [
                    {
                      "id": "project_controller_list",
                      "label": ".list()",
                      "source_file": "src/main/java/com/example/web/ProjectController.java",
                      "source_location": "L8"
                    },
                    {
                      "id": "service_call",
                      "label": ".findAll()",
                      "source_file": "src/main/java/com/example/service/ProjectService.java",
                      "source_location": "L12"
                    }
                  ],
                  "links": [
                    {
                      "relation": "calls",
                      "confidence": "EXTRACTED",
                      "source_file": "src/main/java/com/example/web/ProjectController.java",
                      "source_location": "L9",
                      "source": "project_controller_list",
                      "target": "service_call"
                    }
                  ]
                }
                """);

        List<EndpointSequenceDiagramService.EndpointOption> endpoints = service.listEndpoints(project);

        String mermaid = service.generateMermaid(project, endpoints.get(0).id(), "graphify");

        assertThat(mermaid).startsWith("sequenceDiagram");
        assertThat(mermaid).contains("Spring MVC Route");
        assertThat(mermaid).contains("Graphify node src/main/java/com/example/web/ProjectController.java L8");
        assertThat(mermaid).contains("calls .findAll()");
    }
}
