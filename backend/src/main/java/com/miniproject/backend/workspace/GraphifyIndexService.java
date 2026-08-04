package com.miniproject.backend.workspace;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class GraphifyIndexService {

    private static final List<String> CODE_EXTENSIONS = List.of(
            ".java", ".php", ".js", ".jsx", ".ts", ".tsx", ".py", ".cs", ".rb", ".go",
            ".kt", ".swift", ".m", ".mm", ".h", ".sql", ".xml", ".json",
            ".gradle", ".env", ".gitignore");
    // Directories that never hold source worth indexing -- dependency caches,
    // build output, and (for iOS) compiled framework bundles, which are
    // themselves directories (e.g. CryptoSwift.framework/) full of binaries,
    // not source. Walking the whole project tree (instead of a fixed
    // Laravel-shaped allowlist like app/routes/config/database/src) is what
    // actually finds Swift/Obj-C source living in an arbitrarily-named folder
    // like an Xcode project's "LeadMgmt/" -- see conversation: the old
    // allowlist silently found zero files for pruserveplus-ipad even though
    // .swift was already a supported extension, because "LeadMgmt" was never
    // one of the six hardcoded directory names.
    private static final List<String> SKIP_DIRECTORY_NAMES = List.of(
            "node_modules", "vendor", "graphify-out", "target", "build", "dist", "out",
            "derived data", "deriveddata", "pods");
    private static final List<String> SKIP_DIRECTORY_SUFFIXES = List.of(
            ".framework", ".xcframework", ".embeddedframework", ".xcodeproj", ".xcworkspace");
    private static final Duration TIMEOUT = Duration.ofMinutes(3);

    private final String graphifyCommand;

    public GraphifyIndexService(@Value("${analysis.graphify.command:graphify}") String graphifyCommand) {
        this.graphifyCommand = graphifyCommand;
    }

    public void indexCodeOnly(Path projectPath) {
        if (!Files.isDirectory(projectPath)) {
            throw new IllegalArgumentException("Project path does not exist: " + projectPath);
        }

        backupExistingGraphOutput(projectPath);

        Path staging = createStagingDirectory();
        try {
            int copied = copyCodeCorpus(projectPath, staging);
            if (copied == 0) {
                throw new IllegalArgumentException("No supported code folders found for Graphify indexing.");
            }
            runGraphify(staging);
            copyGraphOutput(staging, projectPath);
        } finally {
            deleteQuietly(staging);
        }
    }

    /**
     * Moves aside any pre-existing graphify-out (a manual `graphify` CLI run
     * from outside this platform, or a previous index of this same path)
     * before starting a fresh run. Without this, hasGraphOutput() sees the
     * old graph.json still sitting there and reconcileGraphifyIndexStatus()
     * marks the workspace "ready" the moment anyone polls /current, before
     * this run has produced anything itself -- confirmed live: this raced
     * ahead of a real 608-file Swift index and reported "ready" in under 5
     * seconds. Renamed, not deleted, so nothing is lost if the fresh run fails.
     */
    private static void backupExistingGraphOutput(Path projectPath) {
        Path existing = projectPath.resolve("graphify-out");
        if (!Files.isDirectory(existing)) {
            return;
        }
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
        Path backup = projectPath.resolve("graphify-out.bak-" + stamp);
        try {
            Files.move(existing, backup);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to back up existing graphify-out before reindexing: " + e.getMessage());
        }
    }

    public boolean hasGraphOutput(Path projectPath) {
        return Files.isRegularFile(projectPath.resolve("graphify-out/graph.json"));
    }

    private int copyCodeCorpus(Path projectPath, Path staging) {
        return copyRecursive(projectPath, staging);
    }

    private void runGraphify(Path staging) {
        List<String> command = new ArrayList<>();
        command.add(graphifyCommand);
        command.add(".");
        command.add("--no-viz");
        command.add("--directed");

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(staging.toFile());
        builder.redirectErrorStream(true);
        try {
            Process process = builder.start();
            boolean finished = process.waitFor(TIMEOUT.toSeconds(), TimeUnit.SECONDS);
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!finished) {
                process.destroyForcibly();
                throw new IllegalStateException("Graphify timed out after " + TIMEOUT.toSeconds() + " seconds.");
            }
            if (process.exitValue() != 0) {
                throw new IllegalStateException("Graphify failed: " + tail(output));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to start Graphify command `" + graphifyCommand + "`: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Graphify indexing was interrupted.");
        }
    }

    private void copyGraphOutput(Path staging, Path projectPath) {
        Path source = staging.resolve("graphify-out/graph.json");
        if (!Files.isRegularFile(source)) {
            throw new IllegalStateException("Graphify completed but graphify-out/graph.json was not created.");
        }
        Path outputDir = projectPath.resolve("graphify-out");
        try {
            Files.createDirectories(outputDir);
            Files.copy(source, outputDir.resolve("graph.json"), StandardCopyOption.REPLACE_EXISTING);
            copyIfPresent(staging.resolve("graphify-out/.graphify_analysis.json"), outputDir.resolve(".graphify_analysis.json"));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to write Graphify output to project: " + e.getMessage());
        }
    }

    private static void copyIfPresent(Path source, Path target) throws IOException {
        if (Files.isRegularFile(source)) {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Path createStagingDirectory() {
        try {
            return Files.createTempDirectory("analyst-graphify-");
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create Graphify staging directory: " + e.getMessage());
        }
    }

    private static int copyRecursive(Path source, Path target) {
        int[] copied = {0};
        try {
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (!dir.equals(source) && shouldSkipDirectory(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    Files.createDirectories(target.resolve(source.relativize(dir)));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!isSupportedCodeFile(file)) {
                        return FileVisitResult.CONTINUE;
                    }
                    copyFile(file, target.resolve(source.relativize(file)));
                    copied[0]++;
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Unable to prepare Graphify code corpus: " + e.getMessage());
        }
        return copied[0];
    }

    private static boolean shouldSkipDirectory(Path dir) {
        String name = dir.getFileName().toString();
        if (name.startsWith(".")) {
            return true;
        }
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        if (SKIP_DIRECTORY_NAMES.contains(lower)) {
            return true;
        }
        return SKIP_DIRECTORY_SUFFIXES.stream().anyMatch(lower::endsWith);
    }

    private static boolean isSupportedCodeFile(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        return CODE_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private static void copyFile(Path source, Path target) {
        try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to copy " + source + ": " + e.getMessage());
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    Files.deleteIfExists(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // Best-effort temp cleanup.
        }
    }

    private static String tail(String output) {
        if (output == null || output.isBlank()) {
            return "no output";
        }
        String normalized = output.replace("\r\n", "\n");
        return normalized.length() <= 1200 ? normalized : normalized.substring(normalized.length() - 1200);
    }
}
