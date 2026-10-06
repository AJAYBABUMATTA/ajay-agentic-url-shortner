package dev.ajaymatta.agentic.execution;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record RollbackAction(UUID id, UUID revisionId, String reason, String expectedManifestHash,
                             String restoredManifestHash, boolean verified, Instant createdAt) {
    public RollbackAction {
        Objects.requireNonNull(id);
        Objects.requireNonNull(revisionId);
        Objects.requireNonNull(createdAt);
        Hashes.requireSha256(expectedManifestHash);
        if (restoredManifestHash != null) Hashes.requireSha256(restoredManifestHash);
        if (reason == null || reason.isBlank() || (verified && !expectedManifestHash.equals(restoredManifestHash))) {
            throw new IllegalArgumentException("Rollback must verify the baseline hash");
        }
    }
}
