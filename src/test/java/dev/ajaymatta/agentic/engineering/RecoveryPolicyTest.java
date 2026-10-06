package dev.ajaymatta.agentic.engineering;

import dev.ajaymatta.agentic.execution.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class RecoveryPolicyTest {
    final RecoveryPolicy policy=new RecoveryPolicy();
    @Test void compilerAndTestRepairsAreBoundedByActualAttemptCount() {
        for(var classification:List.of(BuildEvidence.FailureClassification.COMPILATION,BuildEvidence.FailureClassification.TEST)) {
            assertThat(policy.decide(attempt(1),classification).retry()).isTrue();
            assertThat(policy.decide(attempt(2),classification).retry()).isTrue();
            assertThat(policy.decide(attempt(3),classification).retry()).isFalse();
            assertThat(policy.decide(attempt(1),classification).maximumAttempts()).isEqualTo(3);
        }
    }
    @Test void infrastructureDependencyTimeoutPolicyAndUnknownAreNeverBlindlyRetried() {
        for(var classification:List.of(BuildEvidence.FailureClassification.INFRASTRUCTURE,BuildEvidence.FailureClassification.DEPENDENCY,
                BuildEvidence.FailureClassification.TIMEOUT,BuildEvidence.FailureClassification.POLICY,BuildEvidence.FailureClassification.UNKNOWN)) {
            assertThat(policy.decide(attempt(1),classification).retry()).isFalse();
        }
    }
    private ExecutionAttempt attempt(int number) { return new ExecutionAttempt(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),number,ExecutionAttempt.State.FAILED,"test",List.of(Hashes.sha256("input")),Instant.now(),Instant.now(),"db://failure"); }
}