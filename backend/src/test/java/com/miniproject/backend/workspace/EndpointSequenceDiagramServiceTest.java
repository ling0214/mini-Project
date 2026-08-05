package com.miniproject.backend.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

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

        assertThat(mermaid).startsWith("%%{init:");
        assertThat(mermaid).contains("sequenceDiagram");
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

        assertThat(mermaid).startsWith("%%{init:");
        assertThat(mermaid).contains("sequenceDiagram");
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

        assertThat(mermaid).startsWith("%%{init:");
        assertThat(mermaid).contains("sequenceDiagram");
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

        assertThat(mermaid).startsWith("%%{init:");
        assertThat(mermaid).contains("sequenceDiagram");
        assertThat(mermaid).contains("Graphify node app/Http/Controllers/FloodReportController.php L14");
        assertThat(mermaid).contains("FloodReport model");
        assertThat(mermaid).contains("EXTRACTED calls at app/Http/Controllers/FloodReportController.php L58");
    }

    @Test
    void listsAngularHttpClientCallsResolvedThroughAResourceUrlField(@TempDir Path project) throws Exception {
        Path webapp = project.resolve("src/main/webapp/app/core/user");
        Files.createDirectories(webapp);
        // Verbatim shape from a real JHipster-generated service (UserService):
        // resourceUrl declared once as a field, then referenced by every
        // method instead of writing the path inline.
        Files.writeString(webapp.resolve("user.service.ts"), """
                import { Injectable } from '@angular/core';
                import { HttpClient, HttpResponse } from '@angular/common/http';
                import { Observable } from 'rxjs';
                import { SERVER_API_URL } from 'app/app.constants';
                import { IUser } from './user.model';

                @Injectable({ providedIn: 'root' })
                export class UserService {
                    public resourceUrl = SERVER_API_URL + 'api/users';

                    constructor(private http: HttpClient) {}

                    create(user: IUser): Observable<HttpResponse<IUser>> {
                        return this.http.post<IUser>(this.resourceUrl, user, { observe: 'response' });
                    }

                    find(login: string): Observable<HttpResponse<IUser>> {
                        return this.http.get<IUser>(`${this.resourceUrl}/${login}`, { observe: 'response' });
                    }

                    authorities(): Observable<string[]> {
                        return this.http.get<string[]>(SERVER_API_URL + 'api/users/authorities');
                    }
                }
                """);

        List<EndpointSequenceDiagramService.EndpointOption> endpoints = service.listEndpoints(project);

        assertThat(endpoints)
                .extracting(EndpointSequenceDiagramService.EndpointOption::framework)
                .containsOnly("frontend");
        assertThat(endpoints)
                .extracting(e -> e.method() + " " + e.path())
                .containsExactlyInAnyOrder("POST /api/users", "GET /api/users/{value}", "GET /api/users/authorities");
        assertThat(endpoints)
                .anySatisfy(endpoint -> {
                    assertThat(endpoint.method()).isEqualTo("POST");
                    assertThat(endpoint.controller()).isEqualTo("UserService");
                    assertThat(endpoint.action()).isEqualTo("create");
                });
    }

    @Test
    void crossReferencesAFrontendCallToItsRealBackendController(@TempDir Path root) throws Exception {
        Path frontend = root.resolve("admin-console-frontend");
        Path backend = root.resolve("admin-console-backend");
        Files.createDirectories(frontend.resolve("src/main/webapp/app/core/user"));
        Files.writeString(frontend.resolve("src/main/webapp/app/core/user/user.service.ts"), """
                import { HttpClient } from '@angular/common/http';
                import { SERVER_API_URL } from 'app/app.constants';

                export class UserService {
                    public resourceUrl = SERVER_API_URL + 'api/users';
                    constructor(private http: HttpClient) {}

                    find(login: string): Observable<any> {
                        return this.http.get(`${this.resourceUrl}/${login}`);
                    }
                }
                """);

        Path controllerDir = backend.resolve("src/main/java/com/example/web");
        Files.createDirectories(controllerDir);
        Files.writeString(controllerDir.resolve("UserResource.java"), """
                package com.example.web;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequestMapping("/api/users")
                public class UserResource {
                    @GetMapping("/{login}")
                    public String find() {
                        return userRepository.findOneByLogin(login).orElseThrow();
                    }
                }
                """);

        List<EndpointSequenceDiagramService.EndpointOption> frontendEndpoints = service.listEndpoints(frontend);
        EndpointSequenceDiagramService.EndpointOption findCall = frontendEndpoints.stream()
                .filter(e -> "find".equals(e.action()))
                .findFirst()
                .orElseThrow();

        List<EndpointSequenceDiagramService.EndpointOption> backendEndpoints = service.listEndpoints(backend);
        Optional<EndpointSequenceDiagramService.EndpointOption> match =
                service.findMatchingBackendEndpoint(findCall, backendEndpoints);
        assertThat(match).isPresent();
        assertThat(match.get().controller()).isEqualTo("UserResource");

        String mermaid = service.generateCrossReferencedMermaid(frontend, backend, findCall.id());

        assertThat(mermaid).contains("sequenceDiagram");
        assertThat(mermaid).contains("UserService.find()");
        assertThat(mermaid).contains("Spring MVC Route");
        assertThat(mermaid).contains("UserResource.find()");
        assertThat(mermaid).doesNotContain("Backend API");
    }

    @Test
    void crossReferencesABackendRouteToItsFrontendCaller(@TempDir Path root) throws Exception {
        // The reverse direction: starting from a backend endpoint, find the
        // frontend call site that hits it -- same matching logic, just
        // starting from the other side.
        Path frontend = root.resolve("admin-console-frontend");
        Path backend = root.resolve("admin-console-backend");
        Files.createDirectories(frontend.resolve("src/main/webapp/app/core/user"));
        Files.writeString(frontend.resolve("src/main/webapp/app/core/user/user.service.ts"), """
                import { HttpClient } from '@angular/common/http';
                import { SERVER_API_URL } from 'app/app.constants';

                export class UserService {
                    public resourceUrl = SERVER_API_URL + 'api/users';
                    constructor(private http: HttpClient) {}

                    find(login: string): Observable<any> {
                        return this.http.get(`${this.resourceUrl}/${login}`);
                    }
                }
                """);

        Path controllerDir = backend.resolve("src/main/java/com/example/web");
        Files.createDirectories(controllerDir);
        Files.writeString(controllerDir.resolve("UserResource.java"), """
                package com.example.web;

                import org.springframework.web.bind.annotation.GetMapping;
                import org.springframework.web.bind.annotation.RequestMapping;
                import org.springframework.web.bind.annotation.RestController;

                @RestController
                @RequestMapping("/api/users")
                public class UserResource {
                    @GetMapping("/{login}")
                    public String find() {
                        return userRepository.findOneByLogin(login).orElseThrow();
                    }
                }
                """);

        List<EndpointSequenceDiagramService.EndpointOption> backendEndpoints = service.listEndpoints(backend);
        EndpointSequenceDiagramService.EndpointOption findRoute = backendEndpoints.stream()
                .filter(e -> "find".equals(e.action()))
                .findFirst()
                .orElseThrow();

        String mermaid = service.generateCrossReferencedMermaid(backend, frontend, findRoute.id());

        assertThat(mermaid).contains("sequenceDiagram");
        assertThat(mermaid).contains("UserService.find()");
        assertThat(mermaid).contains("UserResource.find()");
        assertThat(mermaid).doesNotContain("Backend API");
    }

    @Test
    void scansEndpointsWhenTheGivenPathIsAlreadyTheSourceRootItself(@TempDir Path root) throws Exception {
        // A workspace sub-path points directly AT src/main/webapp or
        // src/main/java (not at the repo root above them) -- the nested-
        // convention lookups ("{path}/src/main/java") don't apply here since
        // there's no further nesting; the scanner must fall back to scanning
        // the given path itself.
        Path webapp = root.resolve("webapp-subpath");
        Files.createDirectories(webapp.resolve("app/core/user"));
        Files.writeString(webapp.resolve("app/core/user/user.service.ts"), """
                import { HttpClient } from '@angular/common/http';
                import { SERVER_API_URL } from 'app/app.constants';

                export class UserService {
                    public resourceUrl = SERVER_API_URL + 'api/users';
                    constructor(private http: HttpClient) {}

                    authorities(): Observable<string[]> {
                        return this.http.get<string[]>(SERVER_API_URL + 'api/users/authorities');
                    }
                }
                """);

        Path javaSubpath = root.resolve("java-subpath");
        Files.createDirectories(javaSubpath.resolve("com/example/web"));
        Files.writeString(javaSubpath.resolve("com/example/web/ProjectController.java"), """
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

        List<EndpointSequenceDiagramService.EndpointOption> frontendEndpoints = service.listEndpoints(webapp);
        assertThat(frontendEndpoints)
                .extracting(EndpointSequenceDiagramService.EndpointOption::path)
                .contains("/api/users/authorities");

        List<EndpointSequenceDiagramService.EndpointOption> backendEndpoints = service.listEndpoints(javaSubpath);
        assertThat(backendEndpoints)
                .anySatisfy(endpoint -> {
                    assertThat(endpoint.framework()).isEqualTo("spring");
                    assertThat(endpoint.path()).isEqualTo("/projects");
                });
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

        assertThat(mermaid).startsWith("%%{init:");
        assertThat(mermaid).contains("sequenceDiagram");
        assertThat(mermaid).contains("Spring MVC Route");
        assertThat(mermaid).contains("Graphify node src/main/java/com/example/web/ProjectController.java L8");
        assertThat(mermaid).contains("calls .findAll()");
    }
}
