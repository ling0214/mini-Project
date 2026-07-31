package com.miniproject.backend.workspace;

import com.miniproject.backend.mcp.ProjectGraphClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ArchitectureDiagramServiceTest {

    private final ProjectGraphClient graphClient = mock(ProjectGraphClient.class);
    private final ArchitectureDiagramService service = new ArchitectureDiagramService(graphClient);

    @Test
    void rendersNodesAndEdgesFromLayersAndBoundaries() {
        Map<String, Object> architecture = Map.of(
                "layers", List.of(
                        Map.of("name", "Http", "layer", "api"),
                        Map.of("name", "Models", "layer", "internal")),
                "boundaries", List.of(
                        Map.of("from", "Http", "to", "Models", "call_count", 125)));

        String mermaid = service.toMermaid(architecture);

        assertThat(mermaid).startsWith("flowchart LR");
        assertThat(mermaid).contains("Http[\"Http\"]:::api");
        assertThat(mermaid).contains("Models[\"Models\"]:::internal");
        assertThat(mermaid).contains("Http -->|125| Models");
    }

    @Test
    void rendersEmptyRootPackageNameSafely() {
        Map<String, Object> architecture = Map.of(
                "layers", List.of(Map.of("name", "", "layer", "api")),
                "boundaries", List.of());

        String mermaid = service.toMermaid(architecture);

        assertThat(mermaid).contains("(root)");
        assertThat(mermaid).doesNotContain("[\"\"]");
    }

    @Test
    void capsToTopBoundariesByCallCount() {
        List<Map<String, Object>> manyBoundaries = java.util.stream.IntStream.range(0, 60)
                .mapToObj(i -> Map.<String, Object>of("from", "P" + i, "to", "Sink", "call_count", i))
                .toList();
        Map<String, Object> architecture = Map.of("layers", List.of(), "boundaries", manyBoundaries);

        String mermaid = service.toMermaid(architecture);

        long edgeLines = mermaid.lines().filter(line -> line.contains("-->")).count();
        assertThat(edgeLines).isEqualTo(40);
        assertThat(mermaid).contains("|59|");
        assertThat(mermaid).doesNotContain("|0|");
    }

    @Test
    void returnsPlaceholderWhenNoArchitectureData() {
        String mermaid = service.toMermaid(Map.of());

        assertThat(mermaid).contains("No architecture data available yet");
    }
}
