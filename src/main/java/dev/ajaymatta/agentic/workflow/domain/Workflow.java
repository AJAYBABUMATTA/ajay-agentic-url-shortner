package dev.ajaymatta.agentic.workflow.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Workflow(UUID id, int currentRevision, WorkflowStatus status,
                       long version, Instant createdAt, Instant updatedAt) {
    public Workflow {
        Objects.requireNonNull(id);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);
        Objects.requireNonNull(updatedAt);
        if (currentRevision < 1 || version < 0) throw new IllegalArgumentException("Invalid workflow version");
    }
}
