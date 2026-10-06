package dev.ajaymatta.agentic.intelligence;

import dev.ajaymatta.agentic.execution.*;
import dev.ajaymatta.agentic.workflow.api.WorkflowDetails;
import dev.ajaymatta.agentic.workflow.application.WorkflowService;
import dev.ajaymatta.agentic.workflow.domain.*;
import dev.ajaymatta.agentic.workflow.persistence.WorkflowRepository;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RevisionService {
    private final WorkflowRepository workflows;
    private final IntelligenceStore store;
    private final WorkflowService service;
    private final JdbcTemplate jdbc;
    public RevisionService(WorkflowRepository workflows, IntelligenceStore store, WorkflowService service, JdbcTemplate jdbc) {
        this.workflows = workflows; this.store = store; this.service = service; this.jdbc = jdbc;
    }
    @Transactional
    public WorkflowDetails clarify(UUID workflowId, RevisionRequests.Clarification request, String actor) {
        var old = lock(workflowId, request.expectedRevision());
        if (old.status() != WorkflowStatus.AWAITING_CLARIFICATION) throw new ResponseStatusException(HttpStatus.CONFLICT, "Workflow is not awaiting clarification");
        var analysis = store.artifact(old.id(), EngineeringArtifact.ArtifactType.REQUIREMENT_ANALYSIS)
                .map(a -> store.decode(a.content(), RequirementAnalysis.class)).orElseThrow();
        Set<String> required = new HashSet<>(analysis.questions().stream().map(RequirementAnalysis.Question::id).toList());
        if (!request.answers().keySet().equals(required)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Answer each outstanding question using its exact identifier");
        Map<String, String> answers = priorAnswers(old.id());
        answers.putAll(request.answers());
        String updated = old.requirement() + "\nClarification: " + store.encode(new TreeMap<>(request.answers()));
        if (updated.length() > 10000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Clarified requirement exceeds size limit");
        return revise(old, updated, answers, actor, "Requirement clarification");
    }
    @Transactional
    public WorkflowDetails replan(UUID workflowId, RevisionRequests.Replan request, String actor) {
        var old = lock(workflowId, request.expectedRevision());
        if (!List.of(WorkflowStatus.AWAITING_CHANGE_APPROVAL, WorkflowStatus.SAFE_STOPPED, WorkflowStatus.AWAITING_CLARIFICATION,
                WorkflowStatus.AWAITING_RELEASE_APPROVAL,WorkflowStatus.RELEASE_READY,WorkflowStatus.ROLLED_BACK,WorkflowStatus.CANCELLED).contains(old.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Current revision is processing or cannot be replanned");
        }
        String requirement = request.requirement() == null ? old.requirement() : request.requirement().strip();
        if (requirement.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requirement must be nonblank");
        return revise(old, requirement, request.requirement() == null ? priorAnswers(old.id()) : Map.of(), actor, request.reason());
    }
    private Map<String,String> priorAnswers(UUID revisionId) {
        Map<String,String> answers = new TreeMap<>();
        jdbc.query("SELECT answers FROM clarification_records WHERE revision_id=?", (org.springframework.jdbc.core.RowCallbackHandler) row -> {
            answers.putAll(store.decode(row.getString(1), Answers.class).values());
        }, revisionId);
        return answers;
    }
    private WorkflowRevision lock(UUID workflowId, int expectedRevision) {
        var rows = jdbc.query("SELECT current_revision FROM workflows WHERE id=? FOR UPDATE", (r,n) -> r.getInt(1), workflowId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Workflow not found");
        if (rows.getFirst() != expectedRevision) throw new ResponseStatusException(HttpStatus.CONFLICT, "Stale workflow revision");
        return workflows.revision(workflowId, expectedRevision);
    }
    private WorkflowDetails revise(WorkflowRevision old, String requirement, Map<String,String> answers, String actor, String reason) {
        UUID revisionId = UUID.randomUUID(), taskId = UUID.randomUUID();
        var now = store.now().atOffset(ZoneOffset.UTC);
        jdbc.update("INSERT INTO workflow_revisions(id,workflow_id,revision_number,parent_revision_id,requirement,requirement_hash,repository_path,status,created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                revisionId, old.workflowId(), old.number()+1, old.id(), requirement, Hashes.sha256(requirement), old.repositoryPath(), "RECEIVED", now);
        jdbc.update("INSERT INTO agent_tasks(id,revision_id,task_key,agent_role,state,entry_gates,exit_gates,max_attempts,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                taskId, revisionId, "interpret-requirement", "REQUIREMENT_INTERPRETATION", "PENDING", store.encode(List.of(Gate.CURRENT_INPUT_HASHES)), store.encode(List.of(Gate.ARTIFACT_VALIDATED)), 3, now, now);
        jdbc.update("INSERT INTO clarification_records VALUES (?,?,?,?,?,?,?,?)", UUID.randomUUID(), old.workflowId(), old.id(), revisionId,
                actor, store.encode(new Answers(answers)), reason, now);
        if (!answers.isEmpty()) {
            String content = store.encode(new TreeMap<>(answers));
            store.persistArtifact(new EngineeringArtifact(UUID.randomUUID(), revisionId, taskId, EngineeringArtifact.ArtifactType.REQUIREMENT_ANALYSIS,
                    "clarification/1.0", content, Hashes.sha256(content), List.of(old.requirementHash()), store.now()), "authenticated-human", "clarification-v1");
        }
        jdbc.update("UPDATE engineering_artifacts SET invalidated_at=? WHERE revision_id=? AND NOT (artifact_type IN ('REPOSITORY_MAP','MANIFEST') AND schema_version='1.0') AND invalidated_at IS NULL", now, old.id());
        jdbc.update("UPDATE approvals SET invalidated_at=? WHERE revision_id=? AND invalidated_at IS NULL", now, old.id());
        jdbc.update("UPDATE agent_tasks SET state='INVALIDATED',updated_at=?,version=version+1 WHERE revision_id=? AND state NOT IN ('SUCCEEDED','CANCELLED')", now, old.id());
        jdbc.update("UPDATE workflows SET current_revision=?,status='RECEIVED',version=version+1,updated_at=? WHERE id=?", old.number()+1, now, old.workflowId());
        store.audit(old.workflowId(), revisionId, taskId, "REVISION_CREATED", actor, "parent=" + old.id() + ";reason=" + reason);
        return service.get(old.workflowId());
    }
    public record Answers(Map<String,String> values) { public Answers { values = Map.copyOf(values); } }
}
