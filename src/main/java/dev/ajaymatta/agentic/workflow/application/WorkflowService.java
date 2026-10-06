package dev.ajaymatta.agentic.workflow.application;

import dev.ajaymatta.agentic.execution.AgentRole;
import dev.ajaymatta.agentic.execution.Hashes;
import dev.ajaymatta.agentic.governance.AuditEvent;
import dev.ajaymatta.agentic.intelligence.IntelligenceStore;
import dev.ajaymatta.agentic.repository.RepositoryTools;
import dev.ajaymatta.agentic.workflow.api.SubmitRequirement;
import dev.ajaymatta.agentic.workflow.api.WorkflowDetails;
import dev.ajaymatta.agentic.workflow.domain.AgentTask;
import dev.ajaymatta.agentic.workflow.domain.Gate;
import dev.ajaymatta.agentic.workflow.domain.TaskState;
import dev.ajaymatta.agentic.workflow.domain.Workflow;
import dev.ajaymatta.agentic.workflow.domain.WorkflowRevision;
import dev.ajaymatta.agentic.workflow.domain.WorkflowStatus;
import dev.ajaymatta.agentic.workflow.persistence.WorkflowRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkflowService {
    private final WorkflowRepository repository;
    private final Clock clock;
    private final IntelligenceStore intelligence;

    public WorkflowService(WorkflowRepository repository, Clock clock, IntelligenceStore intelligence) {
        this.repository = repository;
        this.clock = clock;
        this.intelligence = intelligence;
    }

    @Transactional
    public WorkflowDetails submit(SubmitRequirement request) {
        RepositoryTools.validateSelector(request.repositoryPath().strip());
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        UUID workflowId = UUID.randomUUID();
        UUID revisionId = UUID.randomUUID();
        String requirement = request.requirement().strip();
        Workflow workflow = new Workflow(workflowId, 1, WorkflowStatus.RECEIVED, 0, now, now);
        WorkflowRevision revision = new WorkflowRevision(revisionId, workflowId, 1, null, requirement,
                Hashes.sha256(requirement), request.repositoryPath().strip(), WorkflowStatus.RECEIVED, now);
        AgentTask task = new AgentTask(UUID.randomUUID(), revisionId, "interpret-requirement",
                AgentRole.REQUIREMENT_INTERPRETATION, TaskState.PENDING,
                List.of(Gate.CURRENT_INPUT_HASHES), List.of(Gate.ARTIFACT_VALIDATED), 0, 3);
        AuditEvent event = new AuditEvent(UUID.randomUUID(), workflowId, revisionId, task.id(),
                "REQUIREMENT_RECEIVED", "platform:submission", "requirementHash=" + revision.requirementHash(), now);
        repository.insert(workflow, revision, task, event);
        return details(workflow, revision);
    }

    @Transactional(readOnly = true)
    public WorkflowDetails get(UUID workflowId) {
        Workflow workflow = repository.find(workflowId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found"));
        return details(workflow, repository.revision(workflowId, workflow.currentRevision()));
    }

    private WorkflowDetails details(Workflow workflow, WorkflowRevision revision) {
        return new WorkflowDetails(workflow, revision, repository.tasks(revision.id()),
                repository.dependencies(revision.id()), repository.audit(workflow.id()), false, false, intelligence.view(revision.id()));
    }
}
