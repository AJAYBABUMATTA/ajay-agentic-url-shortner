package dev.ajaymatta.agentic.execution;

import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;

/** Identity and approved locations; creation and symlink/root enforcement follow in commit 2. */
public record RepositoryWorkspace(UUID id, UUID revisionId, Path repository, Path baseline,
                                  String baselineManifestHash) {
    public RepositoryWorkspace {
        Objects.requireNonNull(id);
        Objects.requireNonNull(revisionId);
        Objects.requireNonNull(repository);
        Objects.requireNonNull(baseline);
        Hashes.requireSha256(baselineManifestHash);
        if (!repository.isAbsolute() || !baseline.isAbsolute() || repository.normalize().equals(baseline.normalize())) {
            throw new IllegalArgumentException("Workspace and baseline must be distinct absolute locations");
        }
    }
}
