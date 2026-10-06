package dev.ajaymatta.agentic.execution;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Structured proposal only. Filesystem policy must separately approve every operation. */
public record FileOperation(Operation type, String path, String content, String expectedSha256,
                            String reason, String requirementId, List<String> criterionIds,
                            UUID taskId, List<String> inputHashes) {
    public FileOperation {
        Objects.requireNonNull(type);
        Objects.requireNonNull(taskId);
        if (path == null || path.isBlank() || reason == null || reason.isBlank()
                || requirementId == null || requirementId.isBlank()) {
            throw new IllegalArgumentException("Operation requires path, reason and requirement lineage");
        }
        criterionIds = List.copyOf(criterionIds);
        if (criterionIds.isEmpty() || criterionIds.stream().anyMatch(id -> id == null || id.isBlank())) {
            throw new IllegalArgumentException("Operation requires acceptance criterion lineage");
        }
        inputHashes = List.copyOf(inputHashes);
        if (inputHashes.isEmpty()) throw new IllegalArgumentException("Operation requires input hashes");
        inputHashes.forEach(Hashes::requireSha256);
        if (type == Operation.CREATE && expectedSha256 != null) {
            throw new IllegalArgumentException("CREATE requires an absent baseline file");
        }
        if (type != Operation.CREATE) Hashes.requireSha256(expectedSha256);
        if (type == Operation.DELETE && content != null) throw new IllegalArgumentException("DELETE has no replacement content");
        if (type != Operation.DELETE && content == null) throw new IllegalArgumentException("Full replacement content required");
    }

    public enum Operation { CREATE, UPDATE, DELETE }
}
