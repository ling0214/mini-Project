package com.miniproject.backend.skills;

import com.miniproject.backend.mcp.McpToolClient;
import com.miniproject.backend.mcp.ProjectGraphClient;
import com.miniproject.backend.workspace.ArchitectureDiagramService;
import com.miniproject.backend.workspace.GraphifyIndexService;
import com.miniproject.backend.workspace.ProjectWorkspaceEntity;
import com.miniproject.backend.workspace.ProjectWorkspaceRepository;
import com.miniproject.backend.workspace.ProjectWorkspaceService;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MyBanjirCareEndToEndSmokeTest {

    @Test
    void declaresIndexesRunsImpactAnalysisAndGeneratesArchitectureDiagram() throws Exception {
        assumeTrue(Boolean.getBoolean("mybanjircare.smoke"),
                "Run with -Dmybanjircare.smoke=true -Dmybanjircare.path=C:/path/to/MyBanjirCare");

        Path repoPath = Path.of(System.getProperty("mybanjircare.path", "C:/tmp/MyBanjirCare"));
        assumeTrue(Files.isDirectory(repoPath), "MyBanjirCare repo path does not exist: " + repoPath);

        String projectName = System.getProperty("mybanjircare.project", "MyBanjirCare-smoke");
        String python = System.getProperty("mcp.python", "../mcp-server/.venv/Scripts/python.exe");

        try (McpToolClient mcpToolClient = new McpToolClient(python, List.of("-m", "mcp_server.server"))) {
            ProjectGraphClient graphClient = mcpToolClient;
            ProjectContextMatcher matcher = new ProjectContextMatcher(projectName, repoPath, graphClient);
            ProjectWorkspaceService workspaceService = new ProjectWorkspaceService(
                    repositoryStub(), matcher, graphClient, new GraphifyIndexService("graphify"));
            try {
                ProjectWorkspaceEntity declared = workspaceService.declare(
                        projectName,
                        "https://github.com/ling0214/MyBanjirCare",
                        repoPath.toString());

                awaitReady(declared);

                ImpactAnalysisSkill impactSkill = new ImpactAnalysisSkill(
                        graphClient,
                        new RuleBasedImpactAnalysisSynthesizer(),
                        matcher);
                ImpactAnalysisResult impact = impactSkill.run(
                        "Donor should be able to filter approved aid request records by city, category, and urgency before responding to help.");
                assertThat(impact.affectedModules()).isNotEmpty();

                String mermaid = new ArchitectureDiagramService(graphClient).generateMermaid(projectName);
                assertThat(mermaid).startsWith("flowchart LR");
                assertThat(mermaid).doesNotContain("No architecture data available yet");
            } finally {
                workspaceService.shutdown();
            }
        }
    }

    private ProjectWorkspaceRepository repositoryStub() {
        ProjectWorkspaceRepository repository = mock(ProjectWorkspaceRepository.class);
        AtomicReference<ProjectWorkspaceEntity> saved = new AtomicReference<>();
        when(repository.findByActiveTrue()).thenReturn(Optional.empty());
        when(repository.findById(any())).thenAnswer(invocation -> Optional.ofNullable(saved.get()));
        when(repository.save(any())).thenAnswer(invocation -> {
            ProjectWorkspaceEntity entity = invocation.getArgument(0);
            saved.set(entity);
            return entity;
        });
        return repository;
    }

    private void awaitReady(ProjectWorkspaceEntity entity) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 120_000;
        while ("indexing".equals(entity.getIndexStatus()) && System.currentTimeMillis() < deadline) {
            Thread.sleep(500);
        }
        assertThat(entity.getIndexStatus()).isEqualTo("ready");
    }
}
