package dev.ajaymatta.agentic.execution;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record EngineeringArtifact(UUID id, UUID revisionId, UUID taskId, ArtifactType type,
                                  String schemaVersion, String content, String sha256,
                                  List<String> inputHashes, Instant createdAt) {
    public EngineeringArtifact {
        Objects.requireNonNull(id);
        Objects.requireNonNull(revisionId);
        Objects.requireNonNull(taskId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(createdAt);
        if (schemaVersion == null || schemaVersion.isBlank()) throw new IllegalArgumentException("Schema missing");
        Objects.requireNonNull(content);
        Hashes.requireSha256(sha256);
        if (!Hashes.sha256(content).equals(sha256)) throw new IllegalArgumentException("Artifact hash mismatch");
        inputHashes = List.copyOf(inputHashes);
        inputHashes.forEach(Hashes::requireSha256);
    }

    public enum ArtifactType {
        REQUIREMENT_ANALYSIS, AMBIGUITY_ANALYSIS, REPOSITORY_MAP, TASK_PLAN, ARCHITECTURE,
        FILE_PROPOSAL, MANIFEST, UNIFIED_DIFF, BUILD_EVIDENCE, DIAGNOSIS, DOCUMENTATION,
        SECURITY_REVIEW, ENGINEERING_OUTCOME
    }
}
