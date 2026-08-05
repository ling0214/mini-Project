package com.miniproject.backend.web;

import com.miniproject.backend.workspace.ArchitectureDiagramService;
import com.miniproject.backend.workspace.EndpointSequenceDiagramService;
import com.miniproject.backend.workspace.FileSystemBrowseService;
import com.miniproject.backend.workspace.ProjectWorkspaceEntity;
import com.miniproject.backend.workspace.ProjectWorkspaceService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.nio.file.Path;

/**
 * Onboarding entry point: the analyst declares which project (local repo
 * path, plus an optional repo URL kept only for display) impact-analysis
 * should read. Replaces the fixed analysis.target-project server config as
 * the way ProjectContextMatcher's active project gets set.
 */
@RestController
@RequestMapping("/api/workspace")
public class ProjectWorkspaceController {

    private final ProjectWorkspaceService service;
    private final ArchitectureDiagramService diagramService;
    private final EndpointSequenceDiagramService endpointSequenceDiagramService;
    private final FileSystemBrowseService browseService;

    public ProjectWorkspaceController(
            ProjectWorkspaceService service,
            ArchitectureDiagramService diagramService,
            EndpointSequenceDiagramService endpointSequenceDiagramService,
            FileSystemBrowseService browseService) {
        this.service = service;
        this.diagramService = diagramService;
        this.endpointSequenceDiagramService = endpointSequenceDiagramService;
        this.browseService = browseService;
    }

    @CrossOrigin(origins = "*")
    @PostMapping
    public ProjectWorkspaceView declare(@RequestBody DeclareWorkspaceRequest request) {
        return ProjectWorkspaceView.of(service.declare(request.name(), request.repoUrl(), request.localPath()));
    }

    @CrossOrigin(origins = "*")
    @GetMapping("/current")
    public ProjectWorkspaceView current() {
        return service.current().map(ProjectWorkspaceView::of).orElse(null);
    }

    @CrossOrigin(origins = "*")
    @GetMapping
    public List<ProjectWorkspaceView> list() {
        return service.listAll().stream().map(ProjectWorkspaceView::of).toList();
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/{id}/activate")
    public ProjectWorkspaceView activate(@PathVariable String id) {
        return ProjectWorkspaceView.of(service.activate(id));
    }

    @CrossOrigin(origins = "*")
    @DeleteMapping("/{id}")
    public void remove(@PathVariable String id) {
        service.remove(id);
    }

    @CrossOrigin(origins = "*")
    @GetMapping("/browse")
    public FileSystemBrowseService.BrowseResult browse(@RequestParam(required = false) String path) {
        return browseService.browse(path);
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/current/reindex")
    public ProjectWorkspaceView reindexCurrent() {
        return ProjectWorkspaceView.of(service.reindexCurrent());
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/current/graphify-index")
    public ProjectWorkspaceView graphifyIndexCurrent() {
        return ProjectWorkspaceView.of(service.graphifyIndexCurrent());
    }

    /**
     * When local_path is a repo root containing several sub-projects (e.g.
     * frontend + backend), Graphify can't index it directly -- the analyst
     * picks which sub-folder to index instead. See
     * ProjectWorkspaceEntity.graphifyIndexPath.
     */
    @CrossOrigin(origins = "*")
    @PostMapping("/current/graphify-index-path")
    public ProjectWorkspaceView graphifyIndexAtPath(@RequestBody GraphifyIndexPathRequest request) {
        return ProjectWorkspaceView.of(service.graphifyIndexAtPath(request.path()));
    }

    public record GraphifyIndexPathRequest(String path) {
    }

    /**
     * Named sub-folders under one project (e.g. "Frontend"/"Backend"/"Admin
     * console") — the general form of the single graphify-index-path override
     * above. Scoped by workspace id (not "current") so the analyst can define
     * these from the switchboard without first switching to that project.
     */
    @CrossOrigin(origins = "*")
    @GetMapping("/{id}/subpaths")
    public List<ProjectWorkspaceSubpathView> listSubpaths(@PathVariable String id) {
        return service.listSubpaths(id).stream().map(ProjectWorkspaceSubpathView::of).toList();
    }

    @CrossOrigin(origins = "*")
    @PostMapping("/{id}/subpaths")
    public ProjectWorkspaceSubpathView addSubpath(@PathVariable String id, @RequestBody AddSubpathRequest request) {
        return ProjectWorkspaceSubpathView.of(service.addSubpath(id, request.label(), request.path()));
    }

    @CrossOrigin(origins = "*")
    @DeleteMapping("/{id}/subpaths/{subpathId}")
    public void removeSubpath(@PathVariable String id, @PathVariable String subpathId) {
        service.removeSubpath(id, subpathId);
    }

    /** Indexes this sub-path's own architecture graph — required once before its diagram can be picked from Project Overview. */
    @CrossOrigin(origins = "*")
    @PostMapping("/{id}/subpaths/{subpathId}/index")
    public ProjectWorkspaceSubpathView indexSubpath(@PathVariable String id, @PathVariable String subpathId) {
        return ProjectWorkspaceSubpathView.of(service.indexSubpath(id, subpathId));
    }

    public record AddSubpathRequest(String label, String path) {
    }

    @CrossOrigin(origins = "*")
    @GetMapping("/current/diagram")
    public DiagramView currentDiagram(@RequestParam(name = "subpath_id", required = false) String subpathId) {
        ProjectWorkspaceEntity current = service.current()
                .orElseThrow(() -> new IllegalArgumentException("No active project workspace"));

        if (subpathId != null && !subpathId.isBlank()) {
            var subpath = service.getSubpath(subpathId);
            if (!"ready".equals(subpath.getIndexStatus())) {
                throw new IllegalArgumentException(
                        "Sub-path \"" + subpath.getLabel() + "\" is not indexed yet (status: " + subpath.getIndexStatus() + ") — index it first.");
            }
            return new DiagramView(diagramService.generateMermaid(subpath.getIndexedProjectName()));
        }

        if (!"ready".equals(current.getIndexStatus())) {
            throw new IllegalArgumentException(
                    "Project is not indexed yet (status: " + current.getIndexStatus() + ") — diagram not available.");
        }
        return new DiagramView(diagramService.generateMermaid(current.getName()));
    }

    @CrossOrigin(origins = "*")
    @GetMapping("/current/endpoints")
    public List<EndpointSequenceDiagramService.EndpointOption> currentEndpoints(
            @RequestParam(name = "subpath_id", required = false) String subpathId) {
        service.current().orElseThrow(() -> new IllegalArgumentException("No active project workspace"));
        return endpointSequenceDiagramService.listEndpoints(resolveScanPath(subpathId));
    }

    @CrossOrigin(origins = "*")
    @GetMapping("/current/endpoints/sequence")
    public DiagramView currentEndpointSequence(
            @RequestParam String endpointId,
            @RequestParam(defaultValue = "scanner") String engine,
            @RequestParam(name = "subpath_id", required = false) String subpathId) {
        service.current().orElseThrow(() -> new IllegalArgumentException("No active project workspace"));
        if (endpointId == null || endpointId.isBlank()) {
            throw new IllegalArgumentException("endpointId is required");
        }
        return new DiagramView(endpointSequenceDiagramService.generateMermaid(resolveScanPath(subpathId), endpointId, engine));
    }

    /** Which folder endpoint scanning reads from: the picked sub-path if one was given, else the whole active project. */
    private Path resolveScanPath(String subpathId) {
        if (subpathId != null && !subpathId.isBlank()) {
            return Path.of(service.getSubpath(subpathId).getPath());
        }
        ProjectWorkspaceEntity current = service.current()
                .orElseThrow(() -> new IllegalArgumentException("No active project workspace"));
        return Path.of(current.getLocalPath());
    }

    /**
     * "See backend endpoint" / "See frontend caller" — works in both
     * directions. The selected endpoint is scoped to subpathId; this looks
     * at every OTHER sub-path defined on the same project and cross-
     * references against the first one that has routes of the OPPOSITE kind
     * (frontend calls if the selected endpoint is backend, backend routes if
     * it's frontend). Doesn't require the analyst to say which sub-path is
     * "the backend" explicitly — with the common two-sub-path case (one
     * frontend, one backend) there's only ever one candidate anyway.
     */
    @CrossOrigin(origins = "*")
    @GetMapping("/current/endpoints/cross-reference")
    public DiagramView currentEndpointCrossReference(
            @RequestParam("endpoint_id") String endpointId,
            @RequestParam(name = "subpath_id", required = false) String subpathId) {
        ProjectWorkspaceEntity current = service.current()
                .orElseThrow(() -> new IllegalArgumentException("No active project workspace"));
        if (endpointId == null || endpointId.isBlank()) {
            throw new IllegalArgumentException("endpointId is required");
        }
        Path currentPath = resolveScanPath(subpathId);

        EndpointSequenceDiagramService.EndpointOption endpoint = endpointSequenceDiagramService.listEndpoints(currentPath).stream()
                .filter(e -> e.id().equals(endpointId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Endpoint not found: " + endpointId));
        boolean wantsBackend = "frontend".equals(endpoint.framework());

        for (var candidate : service.listSubpaths(current.getId())) {
            if (subpathId != null && candidate.getId().equals(subpathId)) {
                continue;
            }
            Path candidatePath = Path.of(candidate.getPath());
            List<EndpointSequenceDiagramService.EndpointOption> candidateEndpoints =
                    endpointSequenceDiagramService.listEndpoints(candidatePath);
            boolean hasWantedKind = wantsBackend
                    ? candidateEndpoints.stream().anyMatch(e -> !"frontend".equals(e.framework()))
                    : candidateEndpoints.stream().anyMatch(e -> "frontend".equals(e.framework()));
            if (hasWantedKind) {
                return new DiagramView(endpointSequenceDiagramService.generateCrossReferencedMermaid(currentPath, candidatePath, endpointId));
            }
        }
        throw new IllegalArgumentException(wantsBackend
                ? "No other sub-path with backend routes was found. Add a \"Backend\" sub-path for this project first (Project Control Center -> Sub-paths)."
                : "No other sub-path with frontend calls was found. Add a \"Frontend\" sub-path for this project first (Project Control Center -> Sub-paths).");
    }

    public record DiagramView(String mermaid) {
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalArgumentException e) {
        return e.getMessage();
    }
}
