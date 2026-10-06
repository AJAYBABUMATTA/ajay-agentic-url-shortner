package dev.ajaymatta.agentic.execution;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Current-revision artifact references; providers receive no process or write capability. */
public record ExecutionContext(UUID workflowId, UUID revisionId, UUID taskId,
                               String requirement, Map<String, EngineeringArtifact> inputs,
                               List<String> inputHashes) {
    public ExecutionContext {
        Objects.requireNonNull(workflowId);
        Objects.requireNonNull(revisionId);
        Objects.requireNonNull(taskId);
        if (requirement == null || requirement.isBlank()) throw new IllegalArgumentException("Requirement missing");
        inputs = Map.copyOf(inputs);
        inputHashes = List.copyOf(inputHashes);
        inputHashes.forEach(Hashes::requireSha256);
        for (EngineeringArtifact artifact : inputs.values()) {
            if (!artifact.revisionId().equals(revisionId) || !inputHashes.contains(artifact.sha256())) {
                throw new IllegalArgumentException("Input artifact is stale or lacks hash lineage");
            }
        }
    }
}
