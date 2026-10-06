package dev.ajaymatta.agentic.governance;

import dev.ajaymatta.agentic.execution.Hashes;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Human decision over one exact artifact. Authorization is separate from this value object. */
public record Approval(UUID id, UUID revisionId, UUID artifactId, Kind kind, String evidenceHash,
                       String actorId, Decision decision, String reason, Instant createdAt) {
    public Approval {
        Objects.requireNonNull(id);
        Objects.requireNonNull(revisionId);
        Objects.requireNonNull(artifactId);
        Objects.requireNonNull(kind);
        Objects.requireNonNull(decision);
        Objects.requireNonNull(createdAt);
        Hashes.requireSha256(evidenceHash);
        if (actorId == null || actorId.isBlank() || reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Approval requires authenticated actor and rationale");
        }
    }

    public enum Kind { CHANGE, RELEASE }
    public enum Decision { APPROVED, REJECTED }
}
