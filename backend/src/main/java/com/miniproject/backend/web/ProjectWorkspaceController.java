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

    @CrossOrigin(origins = "*")
    @GetMapping("/current/diagram")
    public DiagramView currentDiagram() {
        ProjectWorkspaceEntity current = service.current()
                .orElseThrow(() -> new IllegalArgumentException("No active project workspace"));
        if (!"ready".equals(current.getIndexStatus())) {
            throw new IllegalArgumentException(
                    "Project is not indexed yet (status: " + current.getIndexStatus() + ") — diagram not available.");
        }
        return new DiagramView(diagramService.generateMermaid(current.getName()));
    }

    @CrossOrigin(origins = "*")
    @GetMapping("/current/endpoints")
    public List<EndpointSequenceDiagramService.EndpointOption> currentEndpoints() {
        ProjectWorkspaceEntity current = service.current()
                .orElseThrow(() -> new IllegalArgumentException("No active project workspace"));
        return endpointSequenceDiagramService.listEndpoints(Path.of(current.getLocalPath()));
    }

    @CrossOrigin(origins = "*")
    @GetMapping("/current/endpoints/sequence")
    public DiagramView currentEndpointSequence(
            @RequestParam String endpointId,
            @RequestParam(defaultValue = "scanner") String engine) {
        ProjectWorkspaceEntity current = service.current()
                .orElseThrow(() -> new IllegalArgumentException("No active project workspace"));
        if (endpointId == null || endpointId.isBlank()) {
            throw new IllegalArgumentException("endpointId is required");
        }
        return new DiagramView(endpointSequenceDiagramService.generateMermaid(Path.of(current.getLocalPath()), endpointId, engine));
    }

    public record DiagramView(String mermaid) {
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleBadRequest(IllegalArgumentException e) {
        return e.getMessage();
    }
}
