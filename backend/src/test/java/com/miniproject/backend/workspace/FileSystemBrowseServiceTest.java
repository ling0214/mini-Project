package com.miniproject.backend.workspace;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSystemBrowseServiceTest {

    private final FileSystemBrowseService service = new FileSystemBrowseService();

    @Test
    void listsSubdirectoriesSortedByNameAndFlagsGitRepos(@TempDir Path tempDir) throws Exception {
        Files.createDirectory(tempDir.resolve("zeta"));
        Path alpha = Files.createDirectory(tempDir.resolve("alpha"));
        Files.createDirectory(alpha.resolve(".git"));
        Files.createFile(tempDir.resolve("not-a-directory.txt"));

        FileSystemBrowseService.BrowseResult result = service.browse(tempDir.toString());

        assertThat(result.entries()).extracting(FileSystemBrowseService.Entry::name).containsExactly("alpha", "zeta");
        assertThat(result.entries().get(0).gitRepo()).isTrue();
        assertThat(result.entries().get(1).gitRepo()).isFalse();
        assertThat(result.currentPath()).isEqualTo(tempDir.toString());
    }

    @Test
    void reportsCurrentDirectoryAsGitRepoWhenItHasAGitFolder(@TempDir Path tempDir) throws Exception {
        Files.createDirectory(tempDir.resolve(".git"));

        FileSystemBrowseService.BrowseResult result = service.browse(tempDir.toString());

        assertThat(result.currentIsGitRepo()).isTrue();
    }

    @Test
    void defaultsToUserHomeWhenPathBlank() {
        FileSystemBrowseService.BrowseResult result = service.browse("");

        assertThat(result.currentPath()).isEqualTo(System.getProperty("user.home"));
    }

    @Test
    void rejectsNonExistentPath(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("does-not-exist");

        assertThatThrownBy(() -> service.browse(missing.toString()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
