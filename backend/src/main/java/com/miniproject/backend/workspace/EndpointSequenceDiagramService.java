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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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
        Path routes = projectPath.resolve("routes");
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
        Path sourceRoot = projectPath.resolve("src/main/java");
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
        Matcher matcher = FRONTEND_API_CALL.matcher(text);
        while (matcher.find()) {
            String rawPath = matcher.group(2);
            if (rawPath == null || rawPath.isBlank()) {
                continue;
            }
            String path = normalizeFrontendPath(rawPath);
            if (!path.startsWith("/api/")) {
                continue;
            }
            String method = frontendMethod(matcher.group(3));
            String action = nearestFunctionName(text, matcher.start()).orElse("apiCall");
            String component = nearestComponentName(text, matcher.start()).orElse(sourceFile.getFileName().toString());
            String routeFile = sourceLabel(projectPath, sourceFile);
            String key = method + " " + path + " " + routeFile + " " + action;
            if (!seen.add(key)) {
                continue;
            }
            String id = method + " " + path + " -> " + component + "@" + action;
            endpoints.add(new EndpointOption(id, method, path, component, action, routeFile, "frontend"));
        }
        return endpoints;
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
        Matcher matcher = JS_FUNCTION.matcher(text.substring(0, position));
        String found = "";
        while (matcher.find()) {
            found = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        }
        return found.isBlank() ? Optional.empty() : Optional.of(found);
    }

    private static Optional<String> nearestComponentName(String text, int position) {
        Matcher matcher = Pattern.compile("function\\s+([A-Z][A-Za-z0-9_]*)\\s*\\(").matcher(text.substring(0, position));
        String found = "";
        while (matcher.find()) {
            found = matcher.group(1);
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
