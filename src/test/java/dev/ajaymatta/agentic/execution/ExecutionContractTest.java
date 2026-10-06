package dev.ajaymatta.agentic.execution;

import dev.ajaymatta.agentic.governance.Approval;
import dev.ajaymatta.agentic.governance.PolicyDecision;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ExecutionContractTest {
    private final UUID revision = UUID.randomUUID();
    private final UUID task = UUID.randomUUID();
    private final String inputHash = Hashes.sha256("requirement");

    @Test
    void artifactContentMustMatchHash() {
        assertThatThrownBy(() -> artifact(revision, "modified", Hashes.sha256("original")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("hash mismatch");
        assertThat(artifact(revision, "original", Hashes.sha256("original")).sha256()).isEqualTo(Hashes.sha256("original"));
    }

    @Test
    void executorContextRejectsStaleRevisionAndMissingInputLineage() {
        var artifact = artifact(UUID.randomUUID(), "content", Hashes.sha256("content"));
        assertThatThrownBy(() -> context(artifact, List.of(artifact.sha256())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("stale");
        var current = artifact(revision, "content", Hashes.sha256("content"));
        assertThatThrownBy(() -> context(current, List.of(inputHash)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("lineage");
        assertThat(context(current, List.of(current.sha256())).inputs()).containsEntry("input", current);
    }

    @Test
    void createUpdateDeleteHaveDistinctOptimisticHashAndContentContracts() {
        assertThat(operation(FileOperation.Operation.CREATE, "complete-content", null).content()).isEqualTo("complete-content");
        assertThat(operation(FileOperation.Operation.UPDATE, "replacement", inputHash).expectedSha256()).isEqualTo(inputHash);
        assertThat(operation(FileOperation.Operation.DELETE, null, inputHash).content()).isNull();
        assertThatThrownBy(() -> operation(FileOperation.Operation.CREATE, "content", inputHash)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> operation(FileOperation.Operation.UPDATE, "content", null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> operation(FileOperation.Operation.DELETE, "replacement", inputHash)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> operation(FileOperation.Operation.CREATE, null, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fileProposalRequiresCriterionAndInputHashLineage() {
        assertThatThrownBy(() -> new FileOperation(FileOperation.Operation.CREATE, "src/A.java", "content", null,
                "Implement criterion", "REQ-1", List.of(), task, List.of(inputHash))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FileOperation(FileOperation.Operation.CREATE, "src/A.java", "content", null,
                "Implement criterion", "REQ-1", List.of("AC-1"), task, List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void proposalsAreImmutableAndNarrativeOnlyAgentOutputIsImpossible() {
        var operations = new ArrayList<>(List.of(operation(FileOperation.Operation.CREATE, "content", null)));
        var output = new Agent.AgentOutput(List.of(artifact(revision, "{}", Hashes.sha256("{}"))), operations);
        operations.clear();
        assertThat(output.proposals()).hasSize(1);
        assertThatThrownBy(() -> output.proposals().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new Agent.AgentOutput(List.of(), List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void terminalAttemptsRequireEvidenceAndOrderedTime() {
        Instant start = Instant.parse("2026-10-06T12:00:00Z");
        assertThatThrownBy(() -> attempt(ExecutionAttempt.State.SUCCEEDED, start, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> attempt(ExecutionAttempt.State.SUCCEEDED, start, start.minusSeconds(1), "db://evidence"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(attempt(ExecutionAttempt.State.RUNNING, start, null, null).completedAt()).isNull();
        assertThat(attempt(ExecutionAttempt.State.SUCCEEDED, start, start.plusSeconds(1), "db://evidence").state())
                .isEqualTo(ExecutionAttempt.State.SUCCEEDED);
    }

    @Test
    void buildEvidenceCannotHideFailuresOrInventDiscoveredTests() {
        var unavailable = new BuildEvidence.Coverage(false, 0, 0, null);
        assertThatThrownBy(() -> new BuildEvidence(1, Duration.ofSeconds(1), false, "compiler failed", "", List.of(),
                List.of(), List.of(), unavailable, BuildEvidence.FailureClassification.NONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new BuildEvidence(1, Duration.ofSeconds(1), false, "", "", List.of(),
                List.of("actualTest"), List.of("inventedTest"), unavailable, BuildEvidence.FailureClassification.TEST))
                .isInstanceOf(IllegalArgumentException.class);
        var build = new BuildEvidence(1, Duration.ofSeconds(1), false, "assertion failed", "", List.of("src/main/java/A.java"),
                List.of("actualTest"), List.of("actualTest"), unavailable, BuildEvidence.FailureClassification.TEST);
        assertThat(build.failedTests()).containsExactly("actualTest");
        assertThat(build.coverage().available()).isFalse();
        assertThatThrownBy(() -> new BuildEvidence.Coverage(true, 10, 2, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rollbackSuccessRequiresExactBaselineRestoration() {
        assertThatThrownBy(() -> new RollbackAction(UUID.randomUUID(), revision, "Restore baseline", inputHash,
                Hashes.sha256("other"), true, Instant.now())).isInstanceOf(IllegalArgumentException.class);
        assertThat(new RollbackAction(UUID.randomUUID(), revision, "Restore baseline", inputHash, inputHash, true,
                Instant.now()).verified()).isTrue();
        assertThat(new RollbackAction(UUID.randomUUID(), revision, "Restoration failed", inputHash, null, false,
                Instant.now()).verified()).isFalse();
    }

    @Test
    void governanceRequiresExactHashActorAndRationale() {
        UUID artifact = UUID.randomUUID();
        assertThatThrownBy(() -> new Approval(UUID.randomUUID(), revision, artifact, Approval.Kind.RELEASE, "latest",
                "operator", Approval.Decision.APPROVED, "Reviewed", Instant.now())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Approval(UUID.randomUUID(), revision, artifact, Approval.Kind.RELEASE, inputHash,
                "", Approval.Decision.APPROVED, "Reviewed", Instant.now())).isInstanceOf(IllegalArgumentException.class);
        assertThat(new Approval(UUID.randomUUID(), revision, artifact, Approval.Kind.RELEASE, inputHash,
                "operator", Approval.Decision.REJECTED, "Unsafe change", Instant.now()).decision()).isEqualTo(Approval.Decision.REJECTED);
        assertThat(new PolicyDecision(UUID.randomUUID(), revision, "approved-root", "1", inputHash,
                PolicyDecision.Verdict.DENY, "Root not approved", Instant.now()).verdict()).isEqualTo(PolicyDecision.Verdict.DENY);
    }

    @Test
    void recoveryContractsRequireBoundedAttemptsAndReason() {
        assertThatThrownBy(() -> new RetryPolicy.Decision(true, 0, Duration.ZERO, "retry")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy.Decision(true, 3, Duration.ofSeconds(-1), "retry")).isInstanceOf(IllegalArgumentException.class);
        assertThat(new RetryPolicy.Decision(false, 3, Duration.ZERO, "Exhausted").retry()).isFalse();
        assertThatThrownBy(() -> new FallbackStrategy.Decision(FallbackStrategy.Action.SAFE_STOP, "")).isInstanceOf(IllegalArgumentException.class);
        assertThat(new FallbackStrategy.Decision(FallbackStrategy.Action.HUMAN_INTERVENTION, "Unsupported repair").action())
                .isEqualTo(FallbackStrategy.Action.HUMAN_INTERVENTION);
    }

    private EngineeringArtifact artifact(UUID revisionId, String content, String hash) {
        return new EngineeringArtifact(UUID.randomUUID(), revisionId, task, EngineeringArtifact.ArtifactType.TASK_PLAN,
                "1.0", content, hash, List.of(inputHash), Instant.now());
    }

    private ExecutionContext context(EngineeringArtifact artifact, List<String> hashes) {
        return new ExecutionContext(UUID.randomUUID(), revision, task, "requirement", Map.of("input", artifact), hashes);
    }

    private FileOperation operation(FileOperation.Operation type, String content, String expectedHash) {
        return new FileOperation(type, "src/main/java/A.java", content, expectedHash, "Implement criterion", "REQ-1",
                List.of("AC-1"), task, List.of(inputHash));
    }

    private ExecutionAttempt attempt(ExecutionAttempt.State state, Instant start, Instant end, String evidence) {
        return new ExecutionAttempt(UUID.randomUUID(), revision, task, 1, state, "deterministic-v1",
                List.of(inputHash), start, end, evidence);
    }
}
