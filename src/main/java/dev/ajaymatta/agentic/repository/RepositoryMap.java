package dev.ajaymatta.agentic.repository;

import java.util.List;

public record RepositoryMap(String manifestHash, boolean greenfield, List<Component> components,
                            List<DataFlow> dataFlows, List<String> buildFiles, List<String> testFiles,
                            List<String> limitations) {
    public RepositoryMap {
        components = List.copyOf(components); dataFlows = List.copyOf(dataFlows);
        buildFiles = List.copyOf(buildFiles); testFiles = List.copyOf(testFiles); limitations = List.copyOf(limitations);
    }
    public record Component(String path, String name, String kind, List<String> routes) {
        public Component { routes = List.copyOf(routes); }
    }
    public record DataFlow(String fromPath, String toPath, String evidence) {}
}
