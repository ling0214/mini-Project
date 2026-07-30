package com.miniproject.backend.workspace;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Server-side directory listing so "Local repo path" can be picked like
 * SourceTree's import dialog instead of hand-typed. Only viable because this
 * is a single-operator local tool where the backend already runs on the same
 * machine as the analyst (same trust model as the rest of this controller —
 * open CORS, no auth) — a hosted multi-tenant version could not expose this.
 */
@Service
public class FileSystemBrowseService {

    public BrowseResult browse(String rawPath) {
        Path path = resolvePath(rawPath);
        if (!Files.isDirectory(path)) {
            throw new IllegalArgumentException("Not a directory: " + path);
        }

        List<Entry> entries;
        try (Stream<Path> stream = Files.list(path)) {
            entries = stream
                    .filter(this::isVisibleDirectory)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .map(this::toEntry)
                    .toList();
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot list directory: " + path);
        }

        Path parent = path.getParent();
        return new BrowseResult(path.toString(), parent == null ? null : parent.toString(), entries, isGitRepo(path));
    }

    private Path resolvePath(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return Path.of(System.getProperty("user.home"));
        }
        return Path.of(rawPath.trim());
    }

    private boolean isVisibleDirectory(Path p) {
        if (!Files.isDirectory(p)) {
            return false;
        }
        // Files.isHidden() only checks the Windows hidden attribute, which
        // dotfile-convention folders like .claude/.agents don't set — filter
        // the naming convention too so the picker doesn't clutter with tool
        // config dirs a SourceTree-style browser wouldn't show either.
        if (p.getFileName().toString().startsWith(".")) {
            return false;
        }
        try {
            return !Files.isHidden(p);
        } catch (IOException e) {
            return true;
        }
    }

    private Entry toEntry(Path p) {
        return new Entry(p.getFileName().toString(), p.toString(), isGitRepo(p));
    }

    private boolean isGitRepo(Path p) {
        return Files.isDirectory(p.resolve(".git"));
    }

    public record Entry(String name, String path, boolean gitRepo) {
    }

    public record BrowseResult(String currentPath, String parentPath, List<Entry> entries, boolean currentIsGitRepo) {
    }
}
