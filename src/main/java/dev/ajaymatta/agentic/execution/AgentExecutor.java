package dev.ajaymatta.agentic.execution;

import dev.ajaymatta.agentic.workflow.domain.AgentTask;

/** Dispatches only after validating dependencies, policy, revision hashes and entry gates. */
public interface AgentExecutor {
    ExecutionAttempt execute(AgentTask task, ExecutionContext context);
}
