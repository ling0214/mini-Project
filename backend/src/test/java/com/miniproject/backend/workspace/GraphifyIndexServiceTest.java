package com.miniproject.backend.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GraphifyIndexServiceTest {

    @Test
    void indexesCodeOnlyCorpusAndCopiesGraphOutput(@TempDir Path tempDir) throws Exception {
        Path project = tempDir.resolve("project");
        Files.createDirectories(project.resolve("app/Http/Controllers"));
        Files.createDirectories(project.resolve("docs"));
        Files.createDirectories(project.resolve("src/main/java"));
        Files.writeString(project.resolve("app/Http/Controllers/DemoController.php"), "<?php class DemoController {}");
        Files.writeString(project.resolve("docs/notes.md"), "# This should not be staged for code-only graphify");
        Files.writeString(project.resolve("src/main/java/notes.md"), "# This should also be excluded from staged source");
        Files.writeString(project.resolve("src/main/java/application.yml"), "secret: should-not-be-staged");

        Path fakeGraphify = tempDir.resolve("fake-graphify.cmd");
        Files.writeString(fakeGraphify, """
                @echo off
                if exist src\\main\\java\\notes.md exit /b 7
                if exist src\\main\\java\\application.yml exit /b 8
                mkdir graphify-out
                echo {"nodes":[],"links":[]} > graphify-out\\graph.json
                """);

        GraphifyIndexService service = new GraphifyIndexService(fakeGraphify.toString());

        service.indexCodeOnly(project);

        assertThat(project.resolve("graphify-out/graph.json")).exists();
        assertThat(Files.readString(project.resolve("graphify-out/graph.json"))).contains("\"nodes\"");
    }
}
