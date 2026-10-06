package dev.ajaymatta.agentic.repository;

import dev.ajaymatta.agentic.execution.RepositoryWorkspace;
import java.util.List;
import java.util.Map;

public record RepositorySnapshot(RepositoryWorkspace workspace, List<FileEntry> files,
                                 Map<String, String> contents) {
    public RepositorySnapshot { files = List.copyOf(files); contents = Map.copyOf(contents); }
    public record FileEntry(String path, String sha256, long bytes) {}
}
