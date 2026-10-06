package dev.ajaymatta.agentic.workflow.domain;

import java.util.Objects;
import java.util.UUID;

public record TaskDependency(UUID revisionId, UUID taskId, UUID dependsOnTaskId) {
    public TaskDependency {
        Objects.requireNonNull(revisionId);
        Objects.requireNonNull(taskId);
        Objects.requireNonNull(dependsOnTaskId);
        if (taskId.equals(dependsOnTaskId)) throw new IllegalArgumentException("Self dependency");
    }
}
