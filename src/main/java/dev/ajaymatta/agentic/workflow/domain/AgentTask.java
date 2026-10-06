package dev.ajaymatta.agentic.workflow.domain;

import dev.ajaymatta.agentic.execution.AgentRole;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable task snapshot. No public API accepts a task state or completion output. */
public record AgentTask(UUID id, UUID revisionId, String key, AgentRole role, TaskState state,
                        List<Gate> entryGates, List<Gate> exitGates, int attemptCount, int maxAttempts) {
    public AgentTask {
        Objects.requireNonNull(id);
        Objects.requireNonNull(revisionId);
        Objects.requireNonNull(role);
        Objects.requireNonNull(state);
        if (key == null || key.isBlank() || attemptCount < 0 || maxAttempts < 1 || attemptCount > maxAttempts) {
            throw new IllegalArgumentException("Invalid task identity or attempt bounds");
        }
        entryGates = List.copyOf(entryGates);
        exitGates = List.copyOf(exitGates);
    }
}
