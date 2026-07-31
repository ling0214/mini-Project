package com.miniproject.backend.workspace;

import com.miniproject.backend.mcp.ProjectGraphClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns codebase-memory's get_architecture output into a Mermaid flowchart —
 * the "gen diagram" fast-onboarding feature: a newly connected+indexed
 * project can be understood at a glance (packages + call direction) instead
 * of file-by-file. Deliberately reads only `layers` (package -> api/entry/
 * internal) and `boundaries` (package-to-package call counts) since those
 * two arrays alone already give a meaningful, renderable dependency graph;
 * routes/hotspots/clusters are left for a future richer view.
 */
@Service
public class ArchitectureDiagramService {

    private static final int MAX_EDGES = 40;
    private static final Set<String> KNOWN_LAYERS = Set.of("api", "entry", "internal", "core", "leaf");

    private final ProjectGraphClient graphClient;

    public ArchitectureDiagramService(ProjectGraphClient graphClient) {
        this.graphClient = graphClient;
    }

    public String generateMermaid(String project) {
        Map<String, Object> architecture = graphClient.getArchitecture(project, List.of("overview"));
        return toMermaid(architecture);
    }

    String toMermaid(Map<String, Object> architecture) {
        List<Map<String, Object>> layers = asMapList(architecture.get("layers"));
        List<Map<String, Object>> boundaries = asMapList(architecture.get("boundaries"));

        Map<String, String> layerByPackage = new LinkedHashMap<>();
        for (Map<String, Object> layer : layers) {
            String name = displayName(String.valueOf(layer.getOrDefault("name", "")));
            String kind = String.valueOf(layer.getOrDefault("layer", "internal"));
            layerByPackage.put(name, kind);
        }

        List<Map<String, Object>> topBoundaries = boundaries.stream()
                .sorted(Comparator.comparingLong((Map<String, Object> b) -> asLong(b.get("call_count"))).reversed())
                .limit(MAX_EDGES)
                .toList();

        Set<String> nodes = new LinkedHashSet<>(layerByPackage.keySet());
        for (Map<String, Object> boundary : topBoundaries) {
            nodes.add(displayName(String.valueOf(boundary.getOrDefault("from", ""))));
            nodes.add(displayName(String.valueOf(boundary.getOrDefault("to", ""))));
        }

        if (nodes.isEmpty()) {
            return "flowchart LR\n  empty[\"No architecture data available yet\"]";
        }

        StringBuilder sb = new StringBuilder("flowchart LR\n");
        sb.append("  classDef api fill:#e6f2f0,stroke:#0b5f57,color:#0b5f57;\n");
        sb.append("  classDef entry fill:#fbf1dc,stroke:#8a5a00,color:#8a5a00;\n");
        sb.append("  classDef internal fill:#eef1f5,stroke:#4c5a6e,color:#4c5a6e;\n");
        sb.append("  classDef core fill:#e9f7f8,stroke:#1aa6b7,color:#1aa6b7;\n");
        sb.append("  classDef leaf fill:#faece9,stroke:#9c2c1f,color:#9c2c1f;\n");
        sb.append("  classDef other fill:#f3f5f8,stroke:#8793a3,color:#4c5a6e;\n");

        for (String name : nodes) {
            String kind = safeLayerClass(layerByPackage.get(name));
            sb.append("  ").append(nodeId(name)).append("[\"").append(escapeLabel(name)).append("\"]:::")
                    .append(kind).append('\n');
        }
        for (Map<String, Object> boundary : topBoundaries) {
            String from = displayName(String.valueOf(boundary.getOrDefault("from", "")));
            String to = displayName(String.valueOf(boundary.getOrDefault("to", "")));
            long count = asLong(boundary.get("call_count"));
            sb.append("  ").append(nodeId(from)).append(" -->|").append(count).append("| ")
                    .append(nodeId(to)).append('\n');
        }
        return sb.toString();
    }

    /** Falls back to "other" for any layer value get_architecture returns beyond the known set, rather than emitting an undefined Mermaid class. */
    private static String safeLayerClass(String layer) {
        return layer != null && KNOWN_LAYERS.contains(layer) ? layer : "other";
    }

    private static String displayName(String name) {
        return name.isBlank() ? "(root)" : name;
    }

    private static String nodeId(String name) {
        String sanitized = name.replaceAll("[^a-zA-Z0-9_]", "_");
        if (sanitized.isBlank() || Character.isDigit(sanitized.charAt(0))) {
            sanitized = "n_" + sanitized;
        }
        return sanitized;
    }

    private static String escapeLabel(String label) {
        return label.replace("\"", "'");
    }

    private static long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> asMapList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                result.add((Map<String, Object>) map);
            }
        }
        return result;
    }
}
