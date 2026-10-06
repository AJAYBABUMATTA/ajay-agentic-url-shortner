package dev.ajaymatta.agentic.workflow.domain;

public enum TaskState {
    PENDING, READY, RUNNING, AWAITING_APPROVAL, SUCCEEDED, FAILED, CANCELLED, INVALIDATED
}
