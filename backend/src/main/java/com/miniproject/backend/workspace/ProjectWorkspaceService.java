package com.miniproject.backend.workspace;

import com.miniproject.backend.mcp.ProjectGraphClient;
import com.miniproject.backend.skills.ProjectContextMatcher;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProjectWorkspaceService {

    private final ProjectWorkspaceRepository repository;
    private final ProjectWorkspaceSubpathRepository subpathRepository;
    private final ProjectContextMatcher projectContextMatcher;
    private final ProjectGraphClient projectGraphClient;
    private final GraphifyIndexService graphifyIndexService;
    private final ExecutorService indexingExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService graphifyExecutor = Executors.newSingleThreadExecutor();
    private final Set<String> runningGraphifyWorkspaceIds = ConcurrentHashMap.newKeySet();

    public ProjectWorkspaceService(
            ProjectWorkspaceRepository repository,
            ProjectWorkspaceSubpathRepository subpathRepository,
            ProjectContextMatcher projectContextMatcher,
            ProjectGraphClient projectGraphClient,
            GraphifyIndexService graphifyIndexService) {
        this.repository = repository;
        this.subpathRepository = subpathRepository;
        this.projectContextMatcher = projectContextMatcher;
        this.projectGraphClient = projectGraphClient;
        this.graphifyIndexService = graphifyIndexService;
    }

    @Transactional
    public ProjectWorkspaceEntity declare(String name, String repoUrl, String localPath) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (localPath == null || localPath.isBlank()) {
            throw new IllegalArgumentException("localPath is required");
        }
        Path path = Path.of(localPath.trim());
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("localPath does not exist or is not a directory: " + localPath);
        }

        deactivateCurrent();
        ProjectWorkspaceEntity entity = new ProjectWorkspaceEntity(
                UUID.randomUUID().toString(), name.trim(), blankToNull(repoUrl), path.toString(), Instant.now());
        entity.markIndexing();
        ProjectWorkspaceEntity saved = repository.save(entity);
        projectContextMatcher.useWorkspace(saved.getName(), path);
        indexAsync(saved.getId(), saved.getName(), path);
        return saved;
    }

    @Transactional
    public ProjectWorkspaceEntity graphifyIndexCurrent() {
        ProjectWorkspaceEntity target = repository.findByActiveTrue()
                .orElseThrow(() -> new IllegalArgumentException("No active project workspace"));
        Path path = Path.of(target.getEffectiveGraphifyIndexPath());
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("Path no longer exists: " + path);
        }

        if (graphifyIndexService.hasGraphOutput(path)) {
            target.markGraphifyIndexReady();
            return repository.save(target);
        }

        target.markGraphifyIndexing();
        ProjectWorkspaceEntity saved = repository.save(target);
        runningGraphifyWorkspaceIds.add(saved.getId());
        graphifyIndexAsync(saved.getId(), path);
        return saved;
    }

    /**
     * Analyst-picked override for which folder Graphify indexes, used when
     * local_path itself isn't a single indexable codebase (a repo root
     * containing several sub-projects) — see ProjectWorkspaceEntity.graphifyIndexPath.
     * subPath isn't required to be inside local_path: the analyst is the one
     * who knows which sibling/child folder is the real frontend or backend
     * root, this only re-triggers indexing against whatever they picked.
     */
    @Transactional
    public ProjectWorkspaceEntity graphifyIndexAtPath(String subPath) {
        if (subPath == null || subPath.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        ProjectWorkspaceEntity target = repository.findByActiveTrue()
                .orElseThrow(() -> new IllegalArgumentException("No active project workspace"));
        Path path = Path.of(subPath.trim());
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("Not a directory: " + path);
        }

        target.setGraphifyIndexPath(path.toString());
        target.markGraphifyIndexing();
        ProjectWorkspaceEntity saved = repository.save(target);
        runningGraphifyWorkspaceIds.add(saved.getId());
        graphifyIndexAsync(saved.getId(), path);
        return saved;
    }

    @Transactional
    public ProjectWorkspaceEntity activate(String id) {
        ProjectWorkspaceEntity target = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No workspace found for id " + id));
        Path path = Path.of(target.getLocalPath());
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("localPath no longer exists: " + target.getLocalPath());
        }

        repository.findByActiveTrue()
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    existing.deactivate();
                    repository.save(existing);
                });

        target.activate(Instant.now());
        // Retry indexing on switch-back if it never finished last time — a
        // previously "ready" project is assumed already indexed and is left
        // alone, so switching projects back and forth doesn't keep re-indexing.
        boolean needsIndexing = "not_indexed".equals(target.getIndexStatus()) || "failed".equals(target.getIndexStatus());
        if (needsIndexing) {
            target.markIndexing();
        }
        ProjectWorkspaceEntity saved = repository.save(target);
        projectContextMatcher.useWorkspace(saved.getName(), path);
        if (needsIndexing) {
            indexAsync(saved.getId(), saved.getName(), path);
        }
        return saved;
    }

    @Transactional
    public ProjectWorkspaceEntity reindexCurrent() {
        ProjectWorkspaceEntity target = repository.findByActiveTrue()
                .orElseThrow(() -> new IllegalArgumentException("No active project workspace"));
        Path path = Path.of(target.getLocalPath());
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("localPath no longer exists: " + target.getLocalPath());
        }

        target.markIndexing();
        ProjectWorkspaceEntity saved = repository.save(target);
        projectContextMatcher.useWorkspace(saved.getName(), path);
        indexAsync(saved.getId(), saved.getName(), path);
        return saved;
    }

    @Transactional
    public Optional<ProjectWorkspaceEntity> current() {
        Optional<ProjectWorkspaceEntity> current = repository.findByActiveTrue();
        current.ifPresent(this::reconcileGraphifyIndexStatus);
        return current;
    }

    @Transactional(readOnly = true)
    public List<ProjectWorkspaceEntity> listAll() {
        return repository.findAllByOrderByLastActivatedAtDesc();
    }

    /**
     * Deliberately allowed on the active workspace too (analyst removing a
     * project they no longer need) — leaves no active workspace rather than
     * auto-picking another one, same "explicit over implicit" spirit as the
     * rest of this onboarding flow. Only forgets the local declaration; does
     * not delete the repo on disk or its codebase-memory index.
     */
    @Transactional
    public void remove(String id) {
        ProjectWorkspaceEntity target = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No workspace found for id " + id));
        subpathRepository.deleteByWorkspaceId(id);
        repository.delete(target);
    }

    @Transactional(readOnly = true)
    public List<ProjectWorkspaceSubpathEntity> listSubpaths(String workspaceId) {
        return subpathRepository.findByWorkspaceIdOrderByCreatedAtAsc(workspaceId);
    }

    @Transactional(readOnly = true)
    public ProjectWorkspaceSubpathEntity getSubpath(String subpathId) {
        return subpathRepository.findById(subpathId)
                .orElseThrow(() -> new IllegalArgumentException("No sub-path found for id " + subpathId));
    }

    @Transactional
    public ProjectWorkspaceSubpathEntity addSubpath(String workspaceId, String label, String subPath) {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label is required");
        }
        if (subPath == null || subPath.isBlank()) {
            throw new IllegalArgumentException("path is required");
        }
        repository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("No workspace found for id " + workspaceId));
        Path path = Path.of(subPath.trim());
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("Not a directory: " + path);
        }
        ProjectWorkspaceSubpathEntity entity = new ProjectWorkspaceSubpathEntity(
                UUID.randomUUID().toString(), workspaceId, label.trim(), path.toString(), Instant.now());
        return subpathRepository.save(entity);
    }

    @Transactional
    public void removeSubpath(String workspaceId, String subpathId) {
        ProjectWorkspaceSubpathEntity target = subpathRepository.findById(subpathId)
                .orElseThrow(() -> new IllegalArgumentException("No sub-path found for id " + subpathId));
        if (!target.getWorkspaceId().equals(workspaceId)) {
            throw new IllegalArgumentException("Sub-path does not belong to this workspace");
        }
        subpathRepository.delete(target);
    }

    /**
     * Indexes a sub-path's own codebase-memory-mcp architecture graph, under a
     * name distinct from the parent workspace ("{workspace} :: {label}") so
     * Project Overview can hold a ready graph for the whole project AND each
     * sub-path at once, switching between them without re-indexing on every
     * dropdown change. Mirrors indexAsync()'s single-background-thread +
     * repository.findById/save pattern for the same @Transactional-proxy reason.
     */
    @Transactional
    public ProjectWorkspaceSubpathEntity indexSubpath(String workspaceId, String subpathId) {
        ProjectWorkspaceEntity workspace = repository.findById(workspaceId)
                .orElseThrow(() -> new IllegalArgumentException("No workspace found for id " + workspaceId));
        ProjectWorkspaceSubpathEntity subpath = subpathRepository.findById(subpathId)
                .orElseThrow(() -> new IllegalArgumentException("No sub-path found for id " + subpathId));
        if (!subpath.getWorkspaceId().equals(workspaceId)) {
            throw new IllegalArgumentException("Sub-path does not belong to this workspace");
        }
        Path path = Path.of(subpath.getPath());
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("Path no longer exists: " + path);
        }

        String indexedProjectName = sanitizeProjectName(workspace.getName() + "-" + subpath.getLabel());
        subpath.markIndexing(indexedProjectName);
        ProjectWorkspaceSubpathEntity saved = subpathRepository.save(subpath);
        indexSubpathAsync(saved.getId(), indexedProjectName, path);
        return saved;
    }

    /**
     * codebase-memory-mcp normalizes project names differently between its
     * own index_repository and get_architecture tools when the name has
     * spaces or punctuation (e.g. "Foo (test) :: Bar" comes back indexed as
     * "Foo-test-Bar" but queried as "Foo_test_Bar", a 404) -- pre-sanitizing
     * here to the same hyphen-only shape it already settles on removes any
     * room for the two tools to disagree. Same root cause documented for
     * plain workspace names elsewhere in this codebase; this just applies it
     * to the generated "{workspace}-{sub-path label}" names too.
     */
    private static String sanitizeProjectName(String raw) {
        String cleaned = raw.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
        return cleaned.isBlank() ? "project" : cleaned;
    }

    private void indexSubpathAsync(String subpathId, String indexedProjectName, Path path) {
        indexingExecutor.submit(() -> {
            try {
                projectGraphClient.indexProject(path.toString(), indexedProjectName);
                subpathRepository.findById(subpathId).ifPresent(entity -> {
                    entity.markIndexReady();
                    subpathRepository.save(entity);
                });
            } catch (RuntimeException e) {
                subpathRepository.findById(subpathId).ifPresent(entity -> {
                    entity.markIndexFailed(e.getMessage());
                    subpathRepository.save(entity);
                });
            }
        });
    }

    private void deactivateCurrent() {
        repository.findByActiveTrue().ifPresent(existing -> {
            existing.deactivate();
            repository.save(existing);
        });
    }

    /**
     * Runs on a single background thread so declare()/activate() return
     * immediately — indexing a real repo can take well over a minute.
     * Deliberately calls repository.findById/save (not `this.markIndexReady`)
     * to update status: each is its own Spring Data transaction, which avoids
     * the self-invocation trap where @Transactional on a same-class method
     * called via `this` silently does nothing (call bypasses the proxy).
     */
    private void indexAsync(String workspaceId, String name, Path path) {
        indexingExecutor.submit(() -> {
            try {
                projectGraphClient.indexProject(path.toString(), name);
                repository.findById(workspaceId).ifPresent(entity -> {
                    entity.markIndexReady();
                    repository.save(entity);
                });
            } catch (RuntimeException e) {
                repository.findById(workspaceId).ifPresent(entity -> {
                    entity.markIndexFailed(e.getMessage());
                    repository.save(entity);
                });
            }
        });
    }

    private void graphifyIndexAsync(String workspaceId, Path path) {
        graphifyExecutor.submit(() -> {
            try {
                graphifyIndexService.indexCodeOnly(path);
                repository.findById(workspaceId).ifPresent(entity -> {
                    entity.markGraphifyIndexReady();
                    repository.save(entity);
                });
            } catch (RuntimeException e) {
                repository.findById(workspaceId).ifPresent(entity -> {
                    entity.markGraphifyIndexFailed(e.getMessage());
                    repository.save(entity);
                });
            } finally {
                runningGraphifyWorkspaceIds.remove(workspaceId);
            }
        });
    }

    private void reconcileGraphifyIndexStatus(ProjectWorkspaceEntity entity) {
        if ("ready".equals(entity.getGraphifyIndexStatus())) {
            return;
        }
        Path path = Path.of(entity.getEffectiveGraphifyIndexPath());
        if (Files.isDirectory(path) && graphifyIndexService.hasGraphOutput(path)) {
            entity.markGraphifyIndexReady();
            repository.save(entity);
            return;
        }
        if ("indexing".equals(entity.getGraphifyIndexStatus()) && !runningGraphifyWorkspaceIds.contains(entity.getId())) {
            entity.markGraphifyIndexFailed("Graphify indexing did not finish or no graph output was found. Run Graphify index again.");
            repository.save(entity);
        }
    }

    @PreDestroy
    public void shutdown() {
        indexingExecutor.shutdownNow();
        graphifyExecutor.shutdownNow();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
