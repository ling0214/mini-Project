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

    @Test
    void findsSourceInArbitrarilyNamedFolderNotJustTheFixedLaravelAllowlist(@TempDir Path tempDir) throws Exception {
        // Regression test: an Xcode project's real source lives in a folder
        // named after the app (e.g. "LeadMgmt/"), not app/routes/config/
        // database/tests/src -- the old fixed allowlist found zero files here
        // even though .swift was already a supported extension.
        Path project = tempDir.resolve("project");
        Files.createDirectories(project.resolve("LeadMgmt"));
        Files.writeString(project.resolve("LeadMgmt/AppDelegate.swift"), "class AppDelegate {}");

        Path fakeGraphify = tempDir.resolve("fake-graphify.cmd");
        Files.writeString(fakeGraphify, """
                @echo off
                if not exist LeadMgmt\\AppDelegate.swift exit /b 9
                mkdir graphify-out
                echo {"nodes":[],"links":[]} > graphify-out\\graph.json
                """);

        GraphifyIndexService service = new GraphifyIndexService(fakeGraphify.toString());

        service.indexCodeOnly(project);

        assertThat(project.resolve("graphify-out/graph.json")).exists();
    }

    @Test
    void skipsFrameworkAndBuildDirectoriesEvenThoughTheyMayContainSupportedExtensions(@TempDir Path tempDir) throws Exception {
        Path project = tempDir.resolve("project");
        Files.createDirectories(project.resolve("LeadMgmt"));
        Files.createDirectories(project.resolve("CryptoSwift.framework/Headers"));
        Files.createDirectories(project.resolve("node_modules/some-lib"));
        Files.writeString(project.resolve("LeadMgmt/AppDelegate.swift"), "class AppDelegate {}");
        Files.writeString(project.resolve("CryptoSwift.framework/Headers/CryptoSwift.h"), "// compiled framework header, not project source");
        Files.writeString(project.resolve("node_modules/some-lib/index.js"), "// dependency, not project source");

        Path fakeGraphify = tempDir.resolve("fake-graphify.cmd");
        Files.writeString(fakeGraphify, """
                @echo off
                if exist CryptoSwift.framework exit /b 10
                if exist node_modules exit /b 11
                mkdir graphify-out
                echo {"nodes":[],"links":[]} > graphify-out\\graph.json
                """);

        GraphifyIndexService service = new GraphifyIndexService(fakeGraphify.toString());

        service.indexCodeOnly(project);

        assertThat(project.resolve("graphify-out/graph.json")).exists();
    }

    @Test
    void backsUpPreExistingGraphOutputInsteadOfTrustingItAsFresh(@TempDir Path tempDir) throws Exception {
        // Regression test: a stale graphify-out/graph.json already sitting in
        // the project (e.g. from a manual `graphify` CLI run outside this
        // platform) must not be mistaken for this run's result -- otherwise
        // ProjectWorkspaceService.reconcileGraphifyIndexStatus() sees the old
        // file and marks the workspace "ready" before a fresh run finishes.
        Path project = tempDir.resolve("project");
        Files.createDirectories(project.resolve("LeadMgmt"));
        Files.createDirectories(project.resolve("graphify-out"));
        Files.writeString(project.resolve("LeadMgmt/AppDelegate.swift"), "class AppDelegate {}");
        Files.writeString(project.resolve("graphify-out/graph.json"), "{\"nodes\":[\"STALE\"],\"links\":[]}");

        Path fakeGraphify = tempDir.resolve("fake-graphify.cmd");
        Files.writeString(fakeGraphify, """
                @echo off
                mkdir graphify-out
                echo {"nodes":["FRESH"],"links":[]} > graphify-out\\graph.json
                """);

        GraphifyIndexService service = new GraphifyIndexService(fakeGraphify.toString());

        service.indexCodeOnly(project);

        assertThat(Files.readString(project.resolve("graphify-out/graph.json"))).contains("FRESH");
        Path backupDir = Files.list(project)
                .filter(p -> p.getFileName().toString().startsWith("graphify-out.bak-"))
                .findFirst()
                .orElseThrow();
        assertThat(Files.readString(backupDir.resolve("graph.json"))).contains("STALE");
    }

    @Test
    void throwsWhenNoSupportedFilesExistAnywhereInTheTree(@TempDir Path tempDir) throws Exception {
        Path project = tempDir.resolve("project");
        Files.createDirectories(project.resolve("docs"));
        Files.writeString(project.resolve("docs/notes.md"), "# no code here");

        GraphifyIndexService service = new GraphifyIndexService("unused-should-never-run");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.indexCodeOnly(project))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No supported code folders found");
    }
}
