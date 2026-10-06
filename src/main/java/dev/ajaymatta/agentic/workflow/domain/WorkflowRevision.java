package dev.ajaymatta.agentic.workflow.domain;

import dev.ajaymatta.agentic.execution.Hashes;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record WorkflowRevision(UUID id, UUID workflowId, int number, UUID parentRevisionId,
                               String requirement, String requirementHash, String repositoryPath,
                               WorkflowStatus status, Instant createdAt) {
    public WorkflowRevision {
        Objects.requireNonNull(id);
        Objects.requireNonNull(workflowId);
        Objects.requireNonNull(status);
        Objects.requireNonNull(createdAt);
        if (number < 1 || requirement == null || requirement.isBlank()) {
            throw new IllegalArgumentException("Revision requires a requirement and positive number");
        }
        Hashes.requireSha256(requirementHash);
        if (!Hashes.sha256(requirement).equals(requirementHash)) {
            throw new IllegalArgumentException("Requirement hash mismatch");
        }
    }
}
