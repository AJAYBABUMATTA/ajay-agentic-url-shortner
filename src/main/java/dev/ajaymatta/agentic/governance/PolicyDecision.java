package dev.ajaymatta.agentic.governance;

import dev.ajaymatta.agentic.execution.Hashes;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PolicyDecision(UUID id, UUID revisionId, String policy, String policyVersion,
                             String subjectHash, Verdict verdict, String reason, Instant createdAt) {
    public PolicyDecision {
        Objects.requireNonNull(id);
        Objects.requireNonNull(revisionId);
        Objects.requireNonNull(verdict);
        Objects.requireNonNull(createdAt);
        Hashes.requireSha256(subjectHash);
        if (policy == null || policy.isBlank() || policyVersion == null || policyVersion.isBlank()
                || reason == null || reason.isBlank()) throw new IllegalArgumentException("Policy requires identity and reason");
    }

    public enum Verdict { ALLOW, DENY, REQUIRE_HUMAN }
}
