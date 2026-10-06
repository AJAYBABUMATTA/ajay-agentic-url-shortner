package dev.ajaymatta.agentic.workflow.api;

import dev.ajaymatta.agentic.governance.AuditEvent;
import dev.ajaymatta.agentic.intelligence.IntelligenceModels;
import dev.ajaymatta.agentic.workflow.domain.AgentTask;
import dev.ajaymatta.agentic.workflow.domain.TaskDependency;
import dev.ajaymatta.agentic.workflow.domain.Workflow;
import dev.ajaymatta.agentic.workflow.domain.WorkflowRevision;
import java.util.List;

public record WorkflowDetails(Workflow workflow, WorkflowRevision revision, List<AgentTask> tasks,
                              List<TaskDependency> dependencies, List<AuditEvent> audit,
                              boolean executionEnabled, boolean sourceMutationAllowed, IntelligenceModels.View intelligence) {
    public WorkflowDetails {
        tasks = List.copyOf(tasks);
        dependencies = List.copyOf(dependencies);
        audit = List.copyOf(audit);
    }
}
