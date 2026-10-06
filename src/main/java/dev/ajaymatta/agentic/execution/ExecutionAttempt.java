package dev.ajaymatta.agentic.execution;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record ExecutionAttempt(UUID id, UUID revisionId, UUID taskId, int number, State state,
                               String executor, List<String> inputHashes,
                               Instant startedAt, Instant completedAt, String evidenceLocation) {
    public ExecutionAttempt {
        Objects.requireNonNull(id);
        Objects.requireNonNull(revisionId);
        Objects.requireNonNull(taskId);
        Objects.requireNonNull(state);
        Objects.requireNonNull(startedAt);
        if (number < 1 || executor == null || executor.isBlank()) throw new IllegalArgumentException("Invalid attempt");
        inputHashes = List.copyOf(inputHashes);
        inputHashes.forEach(Hashes::requireSha256);
        if (state == State.RUNNING && completedAt != null) throw new IllegalArgumentException("Running attempt already ended");
        if (state != State.RUNNING && (completedAt == null || completedAt.isBefore(startedAt)
                || evidenceLocation == null || evidenceLocation.isBlank())) {
            throw new IllegalArgumentException("Terminal attempt requires ordered timestamps and evidence");
        }
    }

    public enum State { RUNNING, SUCCEEDED, FAILED, TIMED_OUT, CANCELLED }
}
