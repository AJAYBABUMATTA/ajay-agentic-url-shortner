package dev.ajaymatta.agentic.execution;

public interface FallbackStrategy {
    Decision decide(ExecutionContext context, BuildEvidence failure);

    enum Action { REPAIR, RETRY, RESTORE_BASELINE, HUMAN_INTERVENTION, SAFE_STOP, TERMINAL_FAILURE }

    record Decision(Action action, String reason) {
        public Decision {
            if (action == null || reason == null || reason.isBlank()) throw new IllegalArgumentException("Fallback requires reason");
        }
    }
}
