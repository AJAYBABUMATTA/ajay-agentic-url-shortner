package dev.ajaymatta.agentic.execution;

import java.time.Duration;

public interface RetryPolicy {
    Decision decide(ExecutionAttempt failedAttempt, BuildEvidence.FailureClassification classification);

    record Decision(boolean retry, int maximumAttempts, Duration delay, String reason) {
        public Decision {
            if (maximumAttempts < 1 || delay == null || delay.isNegative() || reason == null || reason.isBlank()) {
                throw new IllegalArgumentException("Invalid retry bounds");
            }
        }
    }
}
