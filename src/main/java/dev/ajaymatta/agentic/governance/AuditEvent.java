package dev.ajaymatta.agentic.governance;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Append-only application record. Database role hardening is a deployment concern. */
public record AuditEvent(UUID id, UUID workflowId, UUID revisionId, UUID taskId,
                         String eventType, String actorId, String details, Instant createdAt) {
    public AuditEvent {
        Objects.requireNonNull(id);
        Objects.requireNonNull(workflowId);
        Objects.requireNonNull(revisionId);
        Objects.requireNonNull(createdAt);
        if (eventType == null || eventType.isBlank() || actorId == null || actorId.isBlank()
                || details == null) throw new IllegalArgumentException("Audit identity missing");
    }
}
