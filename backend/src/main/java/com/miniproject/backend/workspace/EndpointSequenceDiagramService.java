package com.miniproject.backend.workspace;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
public class EndpointSequenceDiagramService {

    // Mermaid embeds its own theme-generated <style> block directly inside each
    // rendered SVG, scoped to that render's id -- that internal stylesheet
    // structurally outranks any page-level CSS (even with !important), so
    // frontend styles.css overrides of .noteText/.actor/etc previously had no
    // effect. Setting theme colors here, via the init directive Mermaid itself
    // reads, is the only reliable way to control sequence diagram colors.
    private static final String SEQUENCE_THEME_INIT = "%%{init: {'theme':'base', 'themeVariables': {"
            + "'background':'#fbfaf6','primaryColor':'#eef8f7','primaryTextColor':'#14242d',"
            + "'primaryBorderColor':'#246a67','lineColor':'#1b6f74','textColor':'#14242d',"
            + "'actorBkg':'#eef8f7','actorBorder':'#246a67','actorTextColor':'#14242d','actorLineColor':'#374e58',"
            + "'signalColor':'#1b6f74','signalTextColor':'#14242d','labelBoxBkgColor':'#eef8f7',"
            + "'labelBoxBorderColor':'#246a67','labelTextColor':'#14242d','loopTextColor':'#14242d',"
            + "'noteBkgColor':'#fff2cf','noteBorderColor':'#c58c23','noteTextColor':'#14242d',"
            + "'activationBorderColor':'#46738f','activationBkgColor':'#d7e8f4','sequenceNumberColor':'#14242d'"
            + "}}}%%\nsequenceDiagram\n";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern LARAVEL_ROUTE = Pattern.compile(
            "Route::(get|post|put|patch|delete|any)\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*,\\s*\\[\\s*(?:\\\\?App\\\\Http\\\\Controllers\\\\)?([A-Za-z0-9_\\\\]+)::class\\s*,\\s*['\"]([A-Za-z0-9_]+)['\"]\\s*\\]",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern FUNCTION_START = Pattern.compile("function\\s+%s\\s*\\([^)]*\\)\\s*(?::[^\\{]+)?\\{");
    private static final Pattern SPRING_CLASS_MAPPING = Pattern.compile("@RequestMapping\\s*\\(\\s*['\"]([^'\"]*)['\"]\\s*\\)");
    private static final Pattern SPRING_METHOD = Pattern.compile(
            "@(GetMapping|PostMapping|PutMapping|PatchMapping|DeleteMapping|RequestMapping)\\s*(?:\\(([^)]*)\\))?\\s*(?:@[A-Za-z0-9_().,\"'\\s]+\\s*)*public\\s+[^{;]+?\\s+([A-Za-z0-9_]+)\\s*\\(",
            Pattern.DOTALL);
    private static final Pattern SPRING_PATH = Pattern.compile("['\"]([^'\"]+)['\"]");
    private static final Pattern FRONTEND_API_CALL = Pattern.compile(
            "\\bapi\\s*\\(\\s*([`'\"])(.*?)\\1\\s*(?:,\\s*\\{(.*?)}\\s*)?\\)",
            Pattern.DOTALL);
    private static final Pattern FRONTEND_METHOD = Pattern.compile(
            "\\bmethod\\s*:\\s*([`'\"])(GET|POST|PUT|PATCH|DELETE)\\1",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern JS_FUNCTION = Pattern.compile(
            "(?:async\\s+)?function\\s+([A-Za-z0-9_]+)\\s*\\([^)]*\\)\\s*\\{|(?:const|let)\\s+([A-Za-z0-9_]+)\\s*=\\s*(?:async\\s*)?\\([^)]*\\)\\s*=>\\s*\\{",
            Pattern.DOTALL);

    // Angular/JHipster: this.http.get<T>(this.resourceUrl, ...) where
    // `resourceUrl` is a class field declared elsewhere in the same file as
    // `SERVER_API_URL + 'api/...'` -- verified against real generated
    // services (UserService, ActivateService) rather than guessed. The call
    // itself rarely has the literal path inline (unlike this project's own
    // api() helper), so resolving it is a two-step: find the field
    // declarations first, then resolve each call's argument against them.
    private static final Pattern ANGULAR_HTTP_CALL = Pattern.compile(
            "\\.(get|post|put|patch|delete)\\s*(?:<[^>(){}]*>)?\\s*\\(\\s*([^,)]+)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ANGULAR_RESOURCE_URL_FIELD = Pattern.compile(
            "\\b([A-Za-z0-9_]+)\\s*=\\s*SERVER_API_URL\\s*\\+\\s*([`'\"])([^`'\"]*)\\2");
    private static final Pattern ANGULAR_INLINE_CONCAT = Pattern.compile(
            "SERVER_API_URL\\s*\\+\\s*([`'\"])([^`'\"]*)\\1");
    private static final Pattern ANGULAR_QUOTED_LITERAL = Pattern.compile("^([`'\"])(.*)\\1$", Pattern.DOTALL);
    private static final Pattern ANGULAR_TEMPLATE_VAR_REF = Pattern.compile("\\$\\{\\s*(?:this\\.)?([A-Za-z0-9_]+)\\s*}");
    private static final Pattern ANGULAR_BARE_REF = Pattern.compile("^(?:this\\.)?([A-Za-z0-9_]+)$");
    private static final Pattern TS_METHOD_START = Pattern.compile(
            "(?:public\\s+|private\\s+|protected\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s*\\([^()]*\\)\\s*:\\s*[A-Za-z0-9_<>\\[\\],. ]+\\s*\\{",
            Pattern.DOTALL);
    private static final Pattern TS_CLASS_NAME = Pattern.compile("class\\s+([A-Za-z0-9_]+)");

    public List<EndpointOption> listEndpoints(Path projectPath) {
        List<EndpointOption> endpoints = new ArrayList<>();
        endpoints.addAll(listSpringEndpoints(projectPath));
        endpoints.addAll(listLaravelEndpoints(projectPath));
        endpoints.addAll(listFrontendApiCalls(projectPath));
        return endpoints.stream()
                .sorted(Comparator.comparing(EndpointOption::framework)
                        .thenComparing(EndpointOption::path)
                        .thenComparing(EndpointOption::method))
                .toList();
    }

    private List<EndpointOption> listLaravelEndpoints(Path projectPath) {
        // Prefer the conventional <repo-root>/routes folder; if it's not
        // there, the analyst may have pointed a sub-path directly at some
        // other folder that itself holds route files -- scan it directly
        // rather than assuming nothing is there.
        Path nested = projectPath.resolve("routes");
        Path routes = Files.isDirectory(nested) ? nested : projectPath;
        if (!Files.isDirectory(routes)) {
            return List.of();
        }
        List<EndpointOption> endpoints = new ArrayList<>();
        try (Stream<Path> files = Files.walk(routes)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".php"))
                    .forEach(path -> endpoints.addAll(parseRoutes(projectPath, path)));
        } catch (IOException e) {
            return List.of();
        }
        return endpoints.stream()
                .sorted(Comparator.comparing(EndpointOption::path).thenComparing(EndpointOption::method))
                .toList();
    }

    public String generateMermaid(Path projectPath, String endpointId) {
        return generateMermaid(projectPath, endpointId, "scanner");
    }

    public String generateMermaid(Path projectPath, String endpointId, String engine) {
        EndpointOption endpoint = listEndpoints(projectPath).stream()
                .filter(item -> item.id().equals(endpointId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Endpoint not found: " + endpointId));

        if ("frontend".equals(endpoint.framework())) {
            return generateFrontendMermaid(endpoint);
        }

        if ("graphify".equalsIgnoreCase(engine)) {
            return generateGraphifyMermaid(projectPath, endpoint);
        }

        return generateScannerMermaid(projectPath, endpoint);
    }

    /**
     * The frontend and backend endpoint scans are otherwise independent --
     * this is what actually joins them: given an endpoint from either side
     * and a separate folder to scan for the OTHER side's routes, find the
     * matching counterpart and render one combined sequence diagram end-to-
     * end, instead of the frontend diagram's generic "Api->>Backend" black
     * box. Works in both directions: pick a frontend call to find which
     * backend controller it hits, or pick a backend route to find a frontend
     * call site that hits it.
     */
    public String generateCrossReferencedMermaid(Path currentProjectPath, Path otherProjectPath, String endpointId) {
        EndpointOption endpoint = listEndpoints(currentProjectPath).stream()
                .filter(item -> item.id().equals(endpointId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Endpoint not found: " + endpointId));

        if ("frontend".equals(endpoint.framework())) {
            List<EndpointOption> backendEndpoints = listEndpoints(otherProjectPath);
            Optional<EndpointOption> match = findMatchingBackendEndpoint(endpoint, backendEndpoints);
            if (match.isEmpty()) {
                return generateFrontendMermaid(endpoint);
            }
            return generateCombinedMermaid(otherProjectPath, endpoint, match.get());
        }

        List<EndpointOption> frontendEndpoints = listEndpoints(otherProjectPath);
        Optional<EndpointOption> match = findMatchingFrontendEndpoint(endpoint, frontendEndpoints);
        if (match.isEmpty()) {
            throw new IllegalArgumentException(
                    "No frontend call was found for " + endpoint.method() + " " + endpoint.path() + ".");
        }
        return generateCombinedMermaid(currentProjectPath, match.get(), endpoint);
    }

    /** Matches by HTTP method + path shape, treating any {placeholder} segment (Spring's {id}, this scanner's own {value}) as an equivalent wildcard. */
    public Optional<EndpointOption> findMatchingBackendEndpoint(EndpointOption frontendEndpoint, List<EndpointOption> candidateEndpoints) {
        String wantedPath = wildcardPath(frontendEndpoint.path());
        return candidateEndpoints.stream()
                .filter(candidate -> !"frontend".equals(candidate.framework()))
                .filter(candidate -> candidate.method().equalsIgnoreCase(frontendEndpoint.method()) || "ANY".equals(candidate.method()))
                .filter(candidate -> wildcardPath(candidate.path()).equals(wantedPath))
                .findFirst();
    }

    /** The reverse direction of findMatchingBackendEndpoint -- given a backend route, find a frontend call site that hits it (the first one, if several components all call the same endpoint). */
    public Optional<EndpointOption> findMatchingFrontendEndpoint(EndpointOption backendEndpoint, List<EndpointOption> candidateEndpoints) {
        String wantedPath = wildcardPath(backendEndpoint.path());
        return candidateEndpoints.stream()
                .filter(candidate -> "frontend".equals(candidate.framework()))
                .filter(candidate -> candidate.method().equalsIgnoreCase(backendEndpoint.method()))
                .filter(candidate -> wildcardPath(candidate.path()).equals(wantedPath))
                .findFirst();
    }

    private static String wildcardPath(String path) {
        return path.replaceAll("\\{[^}]*}", "{*}");
    }

    private String generateCombinedMermaid(Path backendProjectPath, EndpointOption frontendEndpoint, EndpointOption backendEndpoint) {
        boolean spring = "spring".equals(backendEndpoint.framework());
        Path controllerPath = spring ? Path.of(backendEndpoint.routeFile()) : controllerPath(backendProjectPath, backendEndpoint.controller());
        String controllerBody = spring
                ? readJavaMethodBody(controllerPath, backendEndpoint.action()).orElse("")
                : readPhpMethodBody(controllerPath, backendEndpoint.action()).orElse("");
        List<String> models = detectModels(backendProjectPath, controllerBody);
        boolean validates = containsAny(controllerBody, "->validate(", "$request->validate(", "Validator::", "@Valid", "BindingResult");
        boolean touchesDatabase = containsAny(controllerBody, "DB::", "->where(", "->get(", "->first(", "->save(", "::create(", "::find(",
                "repository.", "Repository", ".save(", ".findBy", ".findAll(");
        boolean notifies = containsAny(controllerBody, "Notification", "Mail::", "->notify(", "event(", "handoff", "external");

        String controllerLabel = shortController(backendEndpoint.controller()) + "." + backendEndpoint.action() + "()";
        String frontendLabel = frontendEndpoint.controller() + "." + frontendEndpoint.action() + "()";

        StringBuilder sb = new StringBuilder(SEQUENCE_THEME_INIT);
        sb.append("  actor Analyst\n");
        sb.append("  participant UI as React/Angular UI\n");
        sb.append("  participant Api as frontend HTTP call\n");
        sb.append("  participant Route as ").append(spring ? "Spring MVC Route" : "Laravel Route").append('\n');
        sb.append("  participant Controller as ").append(escape(controllerLabel)).append('\n');
        if (validates) sb.append("  participant Request as Request validation\n");
        for (String model : models) {
            sb.append("  participant ").append(nodeId(model)).append(" as ").append(escape(model)).append(" model\n");
        }
        if (touchesDatabase) sb.append("  participant DB as Database\n");
        if (notifies) sb.append("  participant Notify as Notification/Mail\n");

        sb.append("  Analyst->>UI: trigger ").append(escape(humanAction(frontendEndpoint.action()))).append('\n');
        sb.append("  UI->>Api: ").append(escape(frontendLabel)).append('\n');
        sb.append("  Note right of UI: ").append(escape(frontendEndpoint.routeFile())).append('\n');
        sb.append("  Api->>Route: ").append(escape(backendEndpoint.method())).append(" ").append(escape(backendEndpoint.path())).append('\n');
        sb.append("  Route->>Controller: dispatch ").append(escape(controllerLabel)).append('\n');
        sb.append("  Note right of Controller: ").append(escape(sourceLabel(backendProjectPath, controllerPath))).append('\n');
        if (validates) {
            sb.append("  Controller->>Request: validate request data\n");
            sb.append("  Request-->>Controller: validated input\n");
        }
        for (String model : models) {
            sb.append("  Controller->>").append(nodeId(model)).append(": read or update ").append(escape(model)).append('\n');
            if (touchesDatabase) {
                sb.append("  ").append(nodeId(model)).append("->>DB: query / persist data\n");
                sb.append("  DB-->>").append(nodeId(model)).append(": records\n");
            }
            sb.append("  ").append(nodeId(model)).append("-->>Controller: domain result\n");
        }
        if (notifies) {
            sb.append("  Controller->>Notify: send notification or email\n");
            sb.append("  Notify-->>Controller: queued / sent\n");
        }
        sb.append("  Controller-->>Api: JSON / status response\n");
        sb.append("  Api-->>UI: parsed response or error\n");
        sb.append("  UI-->>Analyst: refreshed screen\n");
        return sb.toString();
    }

    private String generateScannerMermaid(Path projectPath, EndpointOption endpoint) {
        boolean spring = "spring".equals(endpoint.framework());
        Path controllerPath = spring ? Path.of(endpoint.routeFile()) : controllerPath(projectPath, endpoint.controller());
        String controllerBody = spring
                ? readJavaMethodBody(controllerPath, endpoint.action()).orElse("")
                : readPhpMethodBody(controllerPath, endpoint.action()).orElse("");
        List<String> models = detectModels(projectPath, controllerBody);
        boolean validates = containsAny(controllerBody, "->validate(", "$request->validate(", "Validator::", "@Valid", "BindingResult");
        boolean touchesDatabase = containsAny(controllerBody, "DB::", "->where(", "->get(", "->first(", "->save(", "::create(", "::find(",
                "repository.", "Repository", ".save(", ".findBy", ".findAll(");
        boolean notifies = containsAny(controllerBody, "Notification", "Mail::", "->notify(", "event(", "handoff", "external");
        boolean returnsView = containsAny(controllerBody, "view(", "redirect(", "response()->json", "return back(", "return ", "ResponseEntity");

        String controllerLabel = shortController(endpoint.controller()) + "." + endpoint.action() + "()";
        StringBuilder sb = new StringBuilder(SEQUENCE_THEME_INIT);
        sb.append("  actor Analyst\n");
        sb.append("  participant Browser\n");
        sb.append("  participant Route as ").append(spring ? "Spring MVC Route" : "Laravel Route").append('\n');
        sb.append("  participant Controller as ").append(escape(controllerLabel)).append('\n');
        if (validates) sb.append("  participant Request as Request validation\n");
        for (String model : models) {
            sb.append("  participant ").append(nodeId(model)).append(" as ").append(escape(model)).append(" model\n");
        }
        if (touchesDatabase) sb.append("  participant DB as Database\n");
        if (notifies) sb.append("  participant Notify as Notification/Mail\n");
        if (returnsView) sb.append("  participant Response as View/JSON response\n");

        sb.append("  Analyst->>Browser: Open or submit ").append(escape(endpoint.method())).append(" ").append(escape(endpoint.path())).append('\n');
        sb.append("  Browser->>Route: ").append(escape(endpoint.method())).append(" ").append(escape(endpoint.path())).append('\n');
        sb.append("  Route->>Controller: dispatch ").append(escape(controllerLabel)).append('\n');
        sb.append("  Note right of Controller: ").append(escape(sourceLabel(projectPath, controllerPath))).append('\n');
        if (validates) {
            sb.append("  Controller->>Request: validate request data\n");
            sb.append("  Request-->>Controller: validated input\n");
        }
        for (String model : models) {
            sb.append("  Controller->>").append(nodeId(model)).append(": read or update ").append(escape(model)).append('\n');
            if (touchesDatabase) {
                sb.append("  ").append(nodeId(model)).append("->>DB: query / persist data\n");
                sb.append("  DB-->>").append(nodeId(model)).append(": records\n");
            }
            sb.append("  ").append(nodeId(model)).append("-->>Controller: domain result\n");
        }
        if (notifies) {
            sb.append("  Controller->>Notify: send notification or email\n");
            sb.append("  Notify-->>Controller: queued / sent\n");
        }
        if (returnsView) {
            sb.append("  Controller->>Response: build view, redirect, or JSON\n");
            sb.append("  Response-->>Browser: response\n");
        } else {
            sb.append("  Controller-->>Browser: response\n");
        }
        return sb.toString();
    }

    private String generateFrontendMermaid(EndpointOption endpoint) {
        String label = endpoint.controller() + "." + endpoint.action() + "()";
        StringBuilder sb = new StringBuilder(SEQUENCE_THEME_INIT);
        sb.append("  actor Analyst\n");
        sb.append("  participant UI as React UI\n");
        sb.append("  participant Api as frontend api() helper\n");
        sb.append("  participant Backend as Backend API\n");
        sb.append("  participant State as UI state/render\n");
        sb.append("  Analyst->>UI: trigger ").append(escape(humanAction(endpoint.action()))).append('\n');
        sb.append("  UI->>Api: ").append(escape(label)).append('\n');
        sb.append("  Note right of UI: ").append(escape(endpoint.routeFile())).append('\n');
        sb.append("  Api->>Backend: ").append(escape(endpoint.method())).append(" ").append(escape(endpoint.path())).append('\n');
        sb.append("  Backend-->>Api: JSON / status response\n");
        sb.append("  Api-->>UI: parsed response or error\n");
        sb.append("  UI->>State: update loading, artifact, inbox, or workspace state\n");
        sb.append("  State-->>Analyst: refreshed screen\n");
        return sb.toString();
    }

    private String generateGraphifyMermaid(Path projectPath, EndpointOption endpoint) {
        Path graphPath = projectPath.resolve("graphify-out/graph.json");
        if (!Files.isRegularFile(graphPath)) {
            throw new IllegalArgumentException(
                    "Graphify graph not found. Run `graphify . --no-viz --directed` in the connected project, then reload this diagram.");
        }

        GraphifyGraph graph = readGraphifyGraph(graphPath);
        boolean spring = "spring".equals(endpoint.framework());
        Path controllerPath = spring ? Path.of(endpoint.routeFile()) : controllerPath(projectPath, endpoint.controller());
        String controllerSource = sourceLabel(projectPath, controllerPath);
        GraphifyNode methodNode = graph.findMethod(controllerSource, endpoint.action())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Graphify graph exists, but no method node was found for " + shortController(endpoint.controller()) + "."
                                + endpoint.action() + "(). Re-run Graphify after updating the project."));

        List<GraphifyEdge> outgoing = graph.links().stream()
                .filter(edge -> methodNode.id().equals(edge.source()))
                .filter(edge -> "calls".equals(edge.relation()) || "references".equals(edge.relation()))
                .filter(edge -> graph.nodes().containsKey(edge.target()))
                .sorted(Comparator.comparing(GraphifyEdge::sourceLocation))
                .toList();

        String controllerLabel = shortController(endpoint.controller()) + "." + endpoint.action() + "()";
        StringBuilder sb = new StringBuilder(SEQUENCE_THEME_INIT);
        sb.append("  actor Analyst\n");
        sb.append("  participant Browser\n");
        sb.append("  participant Route as ").append(spring ? "Spring MVC Route" : "Laravel Route").append('\n');
        sb.append("  participant Controller as ").append(escape(controllerLabel)).append('\n');

        List<GraphifyStep> steps = outgoing.stream()
                .map(edge -> toGraphifyStep(graph.nodes().get(edge.target()), edge))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .distinct()
                .limit(8)
                .toList();

        for (GraphifyStep step : steps) {
            sb.append("  participant ").append(step.participantId()).append(" as ").append(escape(step.participantLabel())).append('\n');
        }
        sb.append("  participant Response as View/JSON response\n");

        sb.append("  Analyst->>Browser: Open or submit ").append(escape(endpoint.method())).append(" ").append(escape(endpoint.path())).append('\n');
        sb.append("  Browser->>Route: ").append(escape(endpoint.method())).append(" ").append(escape(endpoint.path())).append('\n');
        sb.append("  Route->>Controller: dispatch ").append(escape(controllerLabel)).append('\n');
        sb.append("  Note right of Controller: Graphify node ").append(escape(methodNode.sourceFile())).append(" ")
                .append(escape(methodNode.sourceLocation())).append('\n');

        if (steps.isEmpty()) {
            sb.append("  Note right of Controller: No outgoing Graphify call/reference edges found for this method\n");
        }
        for (GraphifyStep step : steps) {
            sb.append("  Controller->>").append(step.participantId()).append(": ").append(escape(step.message())).append('\n');
            sb.append("  Note over Controller,").append(step.participantId()).append(": ")
                    .append(escape(step.evidence())).append('\n');
            sb.append("  ").append(step.participantId()).append("-->>Controller: result\n");
        }
        sb.append("  Controller->>Response: return response\n");
        sb.append("  Response-->>Browser: rendered result\n");
        return sb.toString();
    }

    private GraphifyGraph readGraphifyGraph(Path graphPath) {
        try {
            JsonNode root = MAPPER.readTree(graphPath.toFile());
            return GraphifyGraph.from(root);
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to read Graphify graph: " + e.getMessage());
        }
    }

    private Optional<GraphifyStep> toGraphifyStep(GraphifyNode target, GraphifyEdge edge) {
        String targetSource = target.sourceFile() == null ? "" : target.sourceFile();
        String label = target.label();
        if (label == null || label.isBlank()) {
            return Optional.empty();
        }
        String kind;
        String message;
        if ("references".equals(edge.relation()) && "Request".equals(label)) {
            kind = "Request";
            message = "uses request input";
        } else if (targetSource.startsWith("app/Models/")) {
            kind = "Model";
            message = "calls " + label + " model";
        } else if (targetSource.startsWith("app/Notifications/")) {
            kind = "Notify";
            message = "uses notification " + label;
        } else if (label.startsWith(".")) {
            kind = "Method";
            message = "calls " + label;
        } else if ("calls".equals(edge.relation())) {
            kind = "Dependency";
            message = "calls " + label;
        } else {
            return Optional.empty();
        }
        String participantLabel = "Model".equals(kind) ? label + " model" : label;
        String evidence = edge.confidence() + " " + edge.relation() + " at " + emptyFallback(edge.sourceFile(), targetSource)
                + " " + emptyFallback(edge.sourceLocation(), target.sourceLocation());
        return Optional.of(new GraphifyStep(nodeId(kind + "_" + label), participantLabel, message, evidence));
    }

    private List<EndpointOption> listSpringEndpoints(Path projectPath) {
        // Same fallback as listLaravelEndpoints: a sub-path may already BE
        // src/main/java (or some other Java source root) rather than the
        // repo root that contains it.
        Path nested = projectPath.resolve("src/main/java");
        Path sourceRoot = Files.isDirectory(nested) ? nested : projectPath;
        if (!Files.isDirectory(sourceRoot)) {
            return List.of();
        }
        List<EndpointOption> endpoints = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sourceRoot)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> read(path).contains("@RestController"))
                    .forEach(path -> endpoints.addAll(parseSpringController(projectPath, path)));
        } catch (IOException e) {
            return List.of();
        }
        return endpoints.stream()
                .sorted(Comparator.comparing(EndpointOption::path).thenComparing(EndpointOption::method))
                .toList();
    }

    private List<EndpointOption> parseSpringController(Path projectPath, Path controllerFile) {
        String text = read(controllerFile);
        String basePath = firstMatch(SPRING_CLASS_MAPPING, text).orElse("");
        String controller = controllerFile.getFileName().toString().replaceFirst("\\.java$", "");
        List<EndpointOption> endpoints = new ArrayList<>();
        Matcher matcher = SPRING_METHOD.matcher(text);
        while (matcher.find()) {
            String annotation = matcher.group(1);
            String args = matcher.group(2) == null ? "" : matcher.group(2);
            String action = matcher.group(3);
            String method = springHttpMethod(annotation, args);
            String path = normalizePath(basePath + firstPath(args).orElse(""));
            String routeFile = controllerFile.toString();
            String id = method + " " + path + " -> " + controller + "@" + action;
            endpoints.add(new EndpointOption(id, method, path, controller, action, routeFile, "spring"));
        }
        return endpoints;
    }

    private List<EndpointOption> listFrontendApiCalls(Path projectPath) {
        List<Path> roots = frontendSourceRoots(projectPath);
        if (roots.isEmpty()) {
            return List.of();
        }

        List<EndpointOption> endpoints = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Path sourceRoot : roots) {
            try (Stream<Path> files = Files.walk(sourceRoot)) {
                files.filter(Files::isRegularFile)
                        .filter(EndpointSequenceDiagramService::isFrontendFile)
                        .forEach(path -> endpoints.addAll(parseFrontendApiCalls(projectPath, path, seen)));
            } catch (IOException ignored) {
                return List.of();
            }
        }
        return endpoints;
    }

    private List<Path> frontendSourceRoots(Path projectPath) {
        List<Path> roots = new ArrayList<>();
        Path directSrc = projectPath.resolve("src");
        if (Files.isDirectory(directSrc) && looksLikeFrontend(projectPath)) {
            roots.add(directSrc);
        }
        Path nestedFrontendSrc = projectPath.resolve("frontend/src");
        if (Files.isDirectory(nestedFrontendSrc)) {
            roots.add(nestedFrontendSrc);
        }
        // Angular/JHipster convention -- src/main/java (backend) and
        // src/main/webapp (Angular frontend) live in the same repo root.
        Path webapp = projectPath.resolve("src/main/webapp");
        if (Files.isDirectory(webapp)) {
            roots.add(webapp);
        }
        // None of the nested conventions matched -- the analyst likely
        // pointed a sub-path directly at the frontend root itself (e.g. a
        // "Frontend" sub-path set to .../src/main/webapp or a plain Vite
        // src/ folder), rather than at the repo root above it.
        if (roots.isEmpty() && Files.isDirectory(projectPath)) {
            roots.add(projectPath);
        }
        return roots;
    }

    private static boolean looksLikeFrontend(Path projectPath) {
        return Files.isRegularFile(projectPath.resolve("package.json"))
                || Files.isRegularFile(projectPath.resolve("vite.config.js"))
                || Files.isRegularFile(projectPath.resolve("vite.config.ts"))
                || Files.isRegularFile(projectPath.resolve("src/main.jsx"))
                || Files.isRegularFile(projectPath.resolve("src/main.tsx"));
    }

    private static boolean isFrontendFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".js") || name.endsWith(".jsx") || name.endsWith(".ts") || name.endsWith(".tsx");
    }

    private List<EndpointOption> parseFrontendApiCalls(Path projectPath, Path sourceFile, Set<String> seen) {
        String text = read(sourceFile);
        List<EndpointOption> endpoints = new ArrayList<>();
        String routeFile = sourceLabel(projectPath, sourceFile);

        Matcher apiMatcher = FRONTEND_API_CALL.matcher(text);
        while (apiMatcher.find()) {
            addFrontendEndpoint(endpoints, seen, text, sourceFile, routeFile,
                    apiMatcher.start(), apiMatcher.group(2), frontendMethod(apiMatcher.group(3)));
        }

        Map<String, String> resourceUrls = angularResourceUrls(text);
        Matcher angularMatcher = ANGULAR_HTTP_CALL.matcher(text);
        while (angularMatcher.find()) {
            String rawPath = resolveAngularUrl(angularMatcher.group(2), resourceUrls);
            if (rawPath == null) {
                continue;
            }
            addFrontendEndpoint(endpoints, seen, text, sourceFile, routeFile,
                    angularMatcher.start(), rawPath, angularMatcher.group(1).toUpperCase(Locale.ROOT));
        }
        return endpoints;
    }

    private void addFrontendEndpoint(
            List<EndpointOption> endpoints, Set<String> seen, String text, Path sourceFile, String routeFile,
            int matchStart, String rawPath, String method) {
        if (rawPath == null || rawPath.isBlank()) {
            return;
        }
        String path = normalizeFrontendPath(rawPath);
        if (!path.startsWith("/api/")) {
            return;
        }
        String action = nearestFunctionName(text, matchStart).orElse("apiCall");
        String component = nearestComponentName(text, matchStart).orElse(sourceFile.getFileName().toString());
        String key = method + " " + path + " " + routeFile + " " + action;
        if (!seen.add(key)) {
            return;
        }
        String id = method + " " + path + " -> " + component + "@" + action;
        endpoints.add(new EndpointOption(id, method, path, component, action, routeFile, "frontend"));
    }

    /**
     * Angular/JHipster services declare a class field once, e.g.
     * `public resourceUrl = SERVER_API_URL + 'api/users';`, then every method
     * calls this.http.get(this.resourceUrl) / `${this.resourceUrl}/${id}`
     * instead of writing the path inline (unlike this project's own api()
     * helper) -- collect those field declarations per-file first so calls
     * that reference them can be resolved.
     */
    private Map<String, String> angularResourceUrls(String text) {
        Map<String, String> urls = new LinkedHashMap<>();
        Matcher matcher = ANGULAR_RESOURCE_URL_FIELD.matcher(text);
        while (matcher.find()) {
            urls.put(matcher.group(1), matcher.group(3));
        }
        return urls;
    }

    private String resolveAngularUrl(String rawArg, Map<String, String> resourceUrls) {
        String arg = rawArg.trim();

        Matcher concat = ANGULAR_INLINE_CONCAT.matcher(arg);
        if (concat.find()) {
            return concat.group(2);
        }

        Matcher literal = ANGULAR_QUOTED_LITERAL.matcher(arg);
        if (literal.matches()) {
            String body = literal.group(2);
            Matcher varRef = ANGULAR_TEMPLATE_VAR_REF.matcher(body);
            if (varRef.find() && resourceUrls.containsKey(varRef.group(1))) {
                return varRef.replaceFirst(Matcher.quoteReplacement(resourceUrls.get(varRef.group(1))));
            }
            return body;
        }

        Matcher bareRef = ANGULAR_BARE_REF.matcher(arg);
        if (bareRef.matches() && resourceUrls.containsKey(bareRef.group(1))) {
            return resourceUrls.get(bareRef.group(1));
        }
        return null;
    }

    private static String frontendMethod(String options) {
        if (options == null) {
            return "GET";
        }
        Matcher matcher = FRONTEND_METHOD.matcher(options);
        return matcher.find() ? matcher.group(2).toUpperCase(Locale.ROOT) : "GET";
    }

    private static String normalizeFrontendPath(String path) {
        String normalized = path.trim()
                .replaceAll("\\$\\{[^}]+}", "{value}")
                .replaceAll("\\s+", " ");
        return normalizePath(normalized);
    }

    private static Optional<String> nearestFunctionName(String text, int position) {
        String head = text.substring(0, position);
        Matcher matcher = JS_FUNCTION.matcher(head);
        String found = "";
        while (matcher.find()) {
            found = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        }
        if (!found.isBlank()) {
            return Optional.of(found);
        }
        // React/Node function declarations don't match TypeScript class
        // methods like `create(user: IUser): Observable<...> {` -- Angular
        // services are almost entirely this shape, so fall back to it.
        Matcher tsMethod = TS_METHOD_START.matcher(head);
        while (tsMethod.find()) {
            found = tsMethod.group(1);
        }
        return found.isBlank() ? Optional.empty() : Optional.of(found);
    }

    private static Optional<String> nearestComponentName(String text, int position) {
        String head = text.substring(0, position);
        Matcher matcher = Pattern.compile("function\\s+([A-Z][A-Za-z0-9_]*)\\s*\\(").matcher(head);
        String found = "";
        while (matcher.find()) {
            found = matcher.group(1);
        }
        if (!found.isBlank()) {
            return Optional.of(found);
        }
        // Angular services/components are classes, not top-level functions --
        // "nearest enclosing class name" is the equivalent grouping label.
        Matcher tsClass = TS_CLASS_NAME.matcher(head);
        while (tsClass.find()) {
            found = tsClass.group(1);
        }
        return found.isBlank() ? Optional.empty() : Optional.of(found);
    }

    private List<EndpointOption> parseRoutes(Path projectPath, Path routeFile) {
        String text = read(routeFile);
        List<EndpointOption> endpoints = new ArrayList<>();
        Matcher matcher = LARAVEL_ROUTE.matcher(text);
        while (matcher.find()) {
            String method = matcher.group(1).toUpperCase(Locale.ROOT);
            String path = normalizePath(matcher.group(2));
            String controller = matcher.group(3);
            String action = matcher.group(4);
            String route = sourceLabel(projectPath, routeFile);
            String id = method + " " + path + " -> " + controller + "@" + action;
            endpoints.add(new EndpointOption(id, method, path, controller, action, route, "laravel"));
        }
        return endpoints;
    }

    private Path controllerPath(Path projectPath, String controller) {
        String normalized = controller.replace("App\\Http\\Controllers\\", "").replace('\\', '/');
        return projectPath.resolve("app/Http/Controllers").resolve(normalized + ".php");
    }

    private Optional<String> readPhpMethodBody(Path controllerPath, String action) {
        String text = read(controllerPath);
        Matcher matcher = Pattern.compile(String.format(FUNCTION_START.pattern(), Pattern.quote(action))).matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        int start = matcher.end() - 1;
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') depth++;
            if (c == '}') depth--;
            if (depth == 0) {
                return Optional.of(text.substring(start, i + 1));
            }
        }
        return Optional.of(text.substring(start));
    }

    private Optional<String> readJavaMethodBody(Path controllerPath, String action) {
        String text = read(controllerPath);
        Matcher matcher = Pattern.compile("\\s" + Pattern.quote(action) + "\\s*\\([^)]*\\)\\s*\\{").matcher(text);
        if (!matcher.find()) {
            return Optional.empty();
        }
        int start = matcher.end() - 1;
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') depth++;
            if (c == '}') depth--;
            if (depth == 0) {
                return Optional.of(text.substring(start, i + 1));
            }
        }
        return Optional.of(text.substring(start));
    }

    private List<String> detectModels(Path projectPath, String controllerBody) {
        Path modelDir = projectPath.resolve("app/Models");
        if (!Files.isDirectory(modelDir) || controllerBody.isBlank()) {
            return List.of();
        }
        Set<String> models = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(modelDir)) {
            files.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".php"))
                    .map(path -> path.getFileName().toString().replaceFirst("\\.php$", ""))
                    .filter(model -> containsAny(controllerBody, model + "::", "new " + model, "$" + lowerFirst(model)))
                    .limit(5)
                    .forEach(models::add);
        } catch (IOException ignored) {
            return List.of();
        }
        return List.copyOf(models);
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) return true;
        }
        return false;
    }

    private static String normalizePath(String path) {
        String trimmed = path == null ? "" : path.trim();
        String normalized = trimmed.replaceAll("//+", "/");
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private static String sourceLabel(Path root, Path path) {
        try {
            return root.relativize(path).toString().replace('\\', '/');
        } catch (IllegalArgumentException e) {
            return path.toString().replace('\\', '/');
        }
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static String shortController(String controller) {
        int slash = controller.lastIndexOf('\\');
        return slash >= 0 ? controller.substring(slash + 1) : controller;
    }

    private static String nodeId(String value) {
        String sanitized = value.replaceAll("[^a-zA-Z0-9_]", "_");
        if (sanitized.isBlank() || Character.isDigit(sanitized.charAt(0))) {
            sanitized = "n_" + sanitized;
        }
        return sanitized;
    }

    private static String escape(String value) {
        return value.replace(":", "#58;").replace("\n", " ").replace("\"", "'");
    }

    private static String lowerFirst(String value) {
        if (value.isBlank()) return value;
        return value.substring(0, 1).toLowerCase(Locale.ROOT) + value.substring(1);
    }

    private static String humanAction(String action) {
        String spaced = action.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ');
        return spaced.isBlank() ? "frontend action" : spaced.toLowerCase(Locale.ROOT);
    }

    private static String emptyFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Optional<String> firstMatch(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static Optional<String> firstPath(String args) {
        Matcher matcher = SPRING_PATH.matcher(args);
        return matcher.find() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static String springHttpMethod(String annotation, String args) {
        return switch (annotation) {
            case "GetMapping" -> "GET";
            case "PostMapping" -> "POST";
            case "PutMapping" -> "PUT";
            case "PatchMapping" -> "PATCH";
            case "DeleteMapping" -> "DELETE";
            default -> {
                String upper = args.toUpperCase(Locale.ROOT);
                if (upper.contains("POST")) yield "POST";
                if (upper.contains("PUT")) yield "PUT";
                if (upper.contains("PATCH")) yield "PATCH";
                if (upper.contains("DELETE")) yield "DELETE";
                yield "ANY";
            }
        };
    }

    private record GraphifyGraph(java.util.Map<String, GraphifyNode> nodes, List<GraphifyEdge> links) {
        static GraphifyGraph from(JsonNode root) {
            java.util.Map<String, GraphifyNode> nodes = new java.util.LinkedHashMap<>();
            for (JsonNode node : root.path("nodes")) {
                String id = text(node, "id");
                if (!id.isBlank()) {
                    nodes.put(id, new GraphifyNode(
                            id,
                            text(node, "label"),
                            text(node, "source_file"),
                            text(node, "source_location")));
                }
            }

            List<GraphifyEdge> links = new ArrayList<>();
            for (JsonNode link : root.path("links")) {
                String source = text(link, "source");
                String target = text(link, "target");
                if (!source.isBlank() && !target.isBlank()) {
                    links.add(new GraphifyEdge(
                            source,
                            target,
                            text(link, "relation"),
                            text(link, "confidence"),
                            text(link, "source_file"),
                            text(link, "source_location")));
                }
            }
            return new GraphifyGraph(nodes, links);
        }

        Optional<GraphifyNode> findMethod(String controllerSource, String action) {
            String expectedLabel = "." + action + "()";
            return nodes.values().stream()
                    .filter(node -> controllerSource.equals(node.sourceFile()))
                    .filter(node -> expectedLabel.equals(node.label()))
                    .findFirst();
        }

        private static String text(JsonNode node, String field) {
            JsonNode value = node.get(field);
            return value == null || value.isNull() ? "" : value.asText("");
        }
    }

    private record GraphifyNode(String id, String label, String sourceFile, String sourceLocation) {
    }

    private record GraphifyEdge(
            String source,
            String target,
            String relation,
            String confidence,
            String sourceFile,
            String sourceLocation) {
    }

    private record GraphifyStep(String participantId, String participantLabel, String message, String evidence) {
    }

    public record EndpointOption(
            String id, String method, String path, String controller, String action, String routeFile, String framework) {
    }
}
