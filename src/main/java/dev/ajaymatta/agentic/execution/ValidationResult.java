package dev.ajaymatta.agentic.execution;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ValidationResult(UUID id, UUID revisionId, UUID artifactId, String artifactHash,
                               String validator, Status status, String summary,
                               String evidenceLocation, Instant createdAt) {
    public ValidationResult {
        Objects.requireNonNull(id);
        Objects.requireNonNull(revisionId);
        Objects.requireNonNull(artifactId);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);
        Hashes.requireSha256(artifactHash);
        if (validator == null || validator.isBlank() || summary == null || summary.isBlank()
                || evidenceLocation == null || evidenceLocation.isBlank()) {
            throw new IllegalArgumentException("Validation requires validator, summary and evidence");
        }
    }

    public enum Status { PASSED, FAILED, INCONCLUSIVE }
}
