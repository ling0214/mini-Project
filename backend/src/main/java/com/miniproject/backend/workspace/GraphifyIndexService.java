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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class GraphifyIndexService {

    private static final List<String> CODE_DIRECTORIES = List.of(
            "app", "routes", "config", "database", "tests", "src");
    private static final List<String> CODE_FILES = List.of(
            "composer.json", "package.json", "pom.xml", "build.gradle", "settings.gradle");
    private static final List<String> CODE_EXTENSIONS = List.of(
            ".java", ".php", ".js", ".jsx", ".ts", ".tsx", ".py", ".cs", ".rb", ".go",
            ".kt", ".swift", ".sql", ".xml", ".json",
            ".gradle", ".env", ".gitignore");
    private static final Duration TIMEOUT = Duration.ofMinutes(3);

    private final String graphifyCommand;

    public GraphifyIndexService(@Value("${analysis.graphify.command:graphify}") String graphifyCommand) {
        this.graphifyCommand = graphifyCommand;
    }

    public void indexCodeOnly(Path projectPath) {
        if (!Files.isDirectory(projectPath)) {
            throw new IllegalArgumentException("Project path does not exist: " + projectPath);
        }

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

    public boolean hasGraphOutput(Path projectPath) {
        return Files.isRegularFile(projectPath.resolve("graphify-out/graph.json"));
    }

    private int copyCodeCorpus(Path projectPath, Path staging) {
        int copied = 0;
        for (String directory : CODE_DIRECTORIES) {
            Path source = projectPath.resolve(directory);
            if (Files.isDirectory(source)) {
                copyRecursive(source, staging.resolve(directory));
                copied++;
            }
        }
        for (String file : CODE_FILES) {
            Path source = projectPath.resolve(file);
            if (Files.isRegularFile(source)) {
                copyFile(source, staging.resolve(file));
                copied++;
            }
        }
        return copied;
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

    private static void copyRecursive(Path source, Path target) {
        try {
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Files.createDirectories(target.resolve(source.relativize(dir)));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (!isSupportedCodeFile(file)) {
                        return FileVisitResult.CONTINUE;
                    }
                    copyFile(file, target.resolve(source.relativize(file)));
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("Unable to prepare Graphify code corpus: " + e.getMessage());
        }
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
