package dev.ajaymatta.agentic.intelligence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ajaymatta.agentic.execution.*;
import dev.ajaymatta.agentic.planning.EngineeringPlan;
import dev.ajaymatta.agentic.repository.RepositorySnapshot;
import dev.ajaymatta.agentic.workflow.domain.*;
import dev.ajaymatta.agentic.workflow.persistence.WorkflowRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class IntelligenceStore {
    private final JdbcTemplate jdbc;
    private final WorkflowRepository workflows;
    private final ObjectMapper json;
    private final Clock clock;
    public IntelligenceStore(JdbcTemplate jdbc, WorkflowRepository workflows, ObjectMapper json, Clock clock) {
        this.jdbc = jdbc; this.workflows = workflows; this.json = json; this.clock = clock;
    }
    public Instant now() { return clock.instant().truncatedTo(ChronoUnit.MICROS); }
    private OffsetDateTime time() { return now().atOffset(ZoneOffset.UTC); }
    public String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Cannot encode evidence", e); }
    }
    public <T> T decode(String content, Class<T> type) {
        try { return json.readValue(content, type); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Invalid evidence schema", e); }
    }
    public List<UUID> received() {
        return jdbc.query("SELECT id FROM workflows WHERE status='RECEIVED' ORDER BY created_at LIMIT 10", (r,n) -> r.getObject(1, UUID.class));
    }
    @Transactional
    public boolean claim(UUID workflowId) {
        return jdbc.update("UPDATE workflows SET status='INTERPRETING',version=version+1,updated_at=? WHERE id=? AND status='RECEIVED'", time(), workflowId) == 1
                && jdbc.update("UPDATE workflow_revisions SET status='INTERPRETING' WHERE workflow_id=? AND revision_number=(SELECT current_revision FROM workflows WHERE id=?)", workflowId, workflowId) == 1;
    }
    @Transactional
    public void status(UUID workflowId, UUID revisionId, WorkflowStatus status) {
        int changed = jdbc.update("UPDATE workflows SET status=?,version=version+1,updated_at=? WHERE id=? AND current_revision=(SELECT revision_number FROM workflow_revisions WHERE id=?)",
                status.name(), time(), workflowId, revisionId);
        if (changed != 1) throw new IllegalStateException("Revision changed during processing");
        jdbc.update("UPDATE workflow_revisions SET status=? WHERE id=?", status.name(), revisionId);
        audit(workflowId, revisionId, null, "WORKFLOW_" + status.name(), "platform:intelligence", "Current revision state changed");
    }
    @Transactional
    public AgentTask task(UUID revisionId, String key, AgentRole role, List<String> dependencies) {
        var existing = workflows.tasks(revisionId).stream().filter(t -> t.key().equals(key)).findFirst();
        UUID id = UUID.nameUUIDFromBytes((revisionId + ":" + key).getBytes(StandardCharsets.UTF_8));
        if (existing.isPresent()) id = existing.get().id();
        else jdbc.update("INSERT INTO agent_tasks(id,revision_id,task_key,agent_role,state,entry_gates,exit_gates,max_attempts,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                id, revisionId, key, role.name(), "PENDING", encode(List.of(Gate.DEPENDENCIES_SUCCEEDED)), encode(List.of(Gate.ARTIFACT_VALIDATED)), 3, time(), time());
        for (String dependency : dependencies) {
            UUID parent = workflows.tasks(revisionId).stream().filter(t -> t.key().equals(dependency)).findFirst().orElseThrow().id();
            if (jdbc.queryForObject("SELECT COUNT(*) FROM task_dependencies WHERE task_id=? AND depends_on_task_id=?", Integer.class, id, parent) == 0) {
                jdbc.update("INSERT INTO task_dependencies VALUES (?,?,?)", revisionId, id, parent);
            }
        }
        return workflows.tasks(revisionId).stream().filter(t -> t.key().equals(key)).findFirst().orElseThrow();
    }
    @Transactional
    public ExecutionAttempt start(AgentTask task, ExecutionContext context) {
        if (!task.id().equals(context.taskId()) || !task.revisionId().equals(context.revisionId())) throw new IllegalArgumentException("Task context mismatch");
        if (task.state() != TaskState.PENDING || task.attemptCount() >= task.maxAttempts()) throw new IllegalStateException("Task cannot start");
        var states = new HashMap<UUID, TaskState>(); workflows.tasks(task.revisionId()).forEach(t -> states.put(t.id(), t.state()));
        if (workflows.dependencies(task.revisionId()).stream().filter(d -> d.taskId().equals(task.id()))
                .anyMatch(d -> states.get(d.dependsOnTaskId()) != TaskState.SUCCEEDED)) throw new IllegalStateException("Dependency gate closed");
        var revision = workflows.revision(context.workflowId(), workflows.find(context.workflowId()).orElseThrow().currentRevision());
        if (!revision.id().equals(context.revisionId()) || !revision.requirementHash().equals(Hashes.sha256(context.requirement()))) throw new IllegalStateException("Stale stage input");
        int changed = jdbc.update("UPDATE agent_tasks SET state='RUNNING',attempt_count=attempt_count+1,version=version+1,updated_at=? WHERE id=? AND state='PENDING' AND attempt_count=?",
                time(), task.id(), task.attemptCount());
        if (changed != 1) throw new IllegalStateException("Task already claimed");
        var attempt = new ExecutionAttempt(UUID.randomUUID(), task.revisionId(), task.id(), task.attemptCount()+1, ExecutionAttempt.State.RUNNING,
                "deterministic-intelligence-v1", context.inputHashes(), now(), null, null);
        jdbc.update("INSERT INTO execution_attempts(id,revision_id,task_id,attempt_number,executor,state,input_hashes,started_at) VALUES (?,?,?,?,?,?,?,?)",
                attempt.id(), attempt.revisionId(), attempt.taskId(), attempt.number(), attempt.executor(), attempt.state().name(), encode(attempt.inputHashes()), attempt.startedAt().atOffset(ZoneOffset.UTC));
        audit(context.workflowId(), context.revisionId(), task.id(), "AGENT_STARTED", "platform:intelligence", task.role().name());
        return attempt;
    }
    @Transactional
    public ExecutionAttempt finish(ExecutionContext context, ExecutionAttempt attempt, EngineeringArtifact artifact, TaskState state) {
        persistArtifact(artifact, "deterministic", "intelligence-v1");
        jdbc.update("INSERT INTO validation_results VALUES (?,?,?,?,?,?,?,?,?)", UUID.randomUUID(), artifact.revisionId(), artifact.id(), artifact.sha256(),
                "intelligence-schema-and-lineage-v1", "PASSED", "Typed current-revision output and hash lineage validated", "db://engineering_artifacts/" + artifact.id(), time());
        jdbc.update("UPDATE execution_attempts SET state='SUCCEEDED',completed_at=?,evidence_location=? WHERE id=? AND state='RUNNING'", time(), "db://engineering_artifacts/" + artifact.id(), attempt.id());
        jdbc.update("UPDATE agent_tasks SET state=?,updated_at=?,version=version+1 WHERE id=? AND state='RUNNING'", state.name(), time(), attempt.taskId());
        audit(context.workflowId(), context.revisionId(), attempt.taskId(), "AGENT_OUTPUT_VALIDATED", "platform:intelligence", "artifact=" + artifact.id() + ";hash=" + artifact.sha256());
        return new ExecutionAttempt(attempt.id(), attempt.revisionId(), attempt.taskId(), attempt.number(), ExecutionAttempt.State.SUCCEEDED,
                attempt.executor(), attempt.inputHashes(), attempt.startedAt(), now(), "db://engineering_artifacts/" + artifact.id());
    }
    @Transactional
    public void fail(ExecutionContext context, ExecutionAttempt attempt) {
        jdbc.update("UPDATE execution_attempts SET state='FAILED',completed_at=?,evidence_location=? WHERE id=? AND state='RUNNING'", time(), "db://audit_events/workflow/" + context.workflowId(), attempt.id());
        jdbc.update("UPDATE agent_tasks SET state='FAILED',updated_at=? WHERE id=?", time(), attempt.taskId());
        audit(context.workflowId(), context.revisionId(), attempt.taskId(), "AGENT_FAILED", "platform:intelligence", "Stage failed; no completion claim");
    }
    public void persistArtifact(EngineeringArtifact artifact, String provider, String model) {
        jdbc.update("INSERT INTO engineering_artifacts(id,revision_id,task_id,artifact_type,schema_version,content,sha256,input_hashes,producing_agent,provider,model,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                artifact.id(), artifact.revisionId(), artifact.taskId(), artifact.type().name(), artifact.schemaVersion(), artifact.content(), artifact.sha256(), encode(artifact.inputHashes()),
                jdbc.queryForObject("SELECT agent_role FROM agent_tasks WHERE id=?", String.class, artifact.taskId()), provider, model, artifact.createdAt().atOffset(ZoneOffset.UTC));
    }
    public List<EngineeringArtifact> artifacts(UUID revisionId) {
        return jdbc.query("SELECT * FROM engineering_artifacts WHERE revision_id=? AND invalidated_at IS NULL ORDER BY created_at,id", (r,n) -> {
            try {
                return new EngineeringArtifact(r.getObject("id", UUID.class), revisionId, r.getObject("task_id", UUID.class), EngineeringArtifact.ArtifactType.valueOf(r.getString("artifact_type")),
                        r.getString("schema_version"), r.getString("content"), r.getString("sha256"),
                        json.readValue(r.getString("input_hashes"), json.getTypeFactory().constructCollectionType(List.class, String.class)), r.getObject("created_at", OffsetDateTime.class).toInstant());
            } catch (JsonProcessingException e) { throw new IllegalStateException("Stored artifact corruption", e); }
        }, revisionId);
    }
    public Optional<EngineeringArtifact> artifact(UUID revisionId, EngineeringArtifact.ArtifactType type) {
        return artifacts(revisionId).stream().filter(a -> a.type() == type && a.schemaVersion().equals("1.0")).reduce((a,b) -> b);
    }
    @Transactional
    public EngineeringArtifact snapshot(UUID workflowId, AgentTask task, RepositorySnapshot snapshot, String requirementHash) {
        var workspace = snapshot.workspace();
        jdbc.update("INSERT INTO repository_workspaces VALUES (?,?,?,?,?,?)", workspace.id(), workspace.revisionId(), workspace.repository().toString(), workspace.baseline().toString(), workspace.baselineManifestHash(), time());
        String content = encode(new IntelligenceModels.SnapshotInput(workspace.baselineManifestHash(), snapshot.files(), snapshot.contents()));
        var artifact = new EngineeringArtifact(UUID.randomUUID(), task.revisionId(), task.id(), EngineeringArtifact.ArtifactType.MANIFEST,
                "1.0", content, Hashes.sha256(content), List.of(requirementHash), now());
        persistArtifact(artifact, "controlled-tool", "snapshot-v1");
        jdbc.update("INSERT INTO policy_decisions VALUES (?,?,?,?,?,?,?,?)", UUID.randomUUID(), task.revisionId(), "approved-root-snapshot", "1.0",
                workspace.baselineManifestHash(), "ALLOW", "Bounded snapshot and exact baseline copy verified", time());
        audit(workflowId, task.revisionId(), task.id(), "WORKSPACE_SNAPSHOT_VERIFIED", "platform:repository-tool", "manifest=" + workspace.baselineManifestHash());
        return artifact;
    }
    @Transactional
    public void persistPlan(UUID workflowId, EngineeringPlan plan) {
        for (var planned : plan.tasks()) {
            var task = task(plan.revisionId(), planned.key(), planned.role(), List.of());
            jdbc.update("UPDATE agent_tasks SET entry_gates=?,exit_gates=? WHERE id=?", encode(planned.entryGates()), encode(planned.exitGates()), task.id());
        }
        for (var planned : plan.tasks()) task(plan.revisionId(), planned.key(), planned.role(), planned.dependencies());
        status(workflowId, plan.revisionId(), WorkflowStatus.AWAITING_CHANGE_APPROVAL);
    }
    public void audit(UUID workflowId, UUID revisionId, UUID taskId, String event, String actor, String details) {
        jdbc.update("INSERT INTO audit_events VALUES (?,?,?,?,?,?,?,?)", UUID.randomUUID(), workflowId, revisionId, taskId, event, actor, details, time());
    }
    public IntelligenceModels.View view(UUID revisionId) {
        var requirement = artifact(revisionId, EngineeringArtifact.ArtifactType.REQUIREMENT_ANALYSIS).map(a -> decode(a.content(), RequirementAnalysis.class)).orElse(null);
        var repository = artifact(revisionId, EngineeringArtifact.ArtifactType.REPOSITORY_MAP).map(a -> decode(a.content(), dev.ajaymatta.agentic.repository.RepositoryMap.class)).orElse(null);
        var plan = artifact(revisionId, EngineeringArtifact.ArtifactType.TASK_PLAN);
        var invalidated = jdbc.query("SELECT a.id FROM engineering_artifacts a JOIN workflow_revisions r ON a.revision_id=r.id WHERE r.workflow_id=(SELECT workflow_id FROM workflow_revisions WHERE id=?) AND a.invalidated_at IS NOT NULL ORDER BY a.created_at,a.id", (r,n) -> r.getString(1), revisionId);
        var reused = jdbc.query("SELECT reused_artifact_id FROM artifact_reuse WHERE revision_id=? ORDER BY created_at,id", (r,n) -> r.getString(1), revisionId);
        var summaries = artifacts(revisionId).stream().map(a -> new IntelligenceModels.ArtifactSummary(a.id(), a.taskId(), a.type(), a.sha256(), a.inputHashes())).toList();
        List<ExecutionAttempt> attempts = jdbc.query("SELECT * FROM execution_attempts WHERE revision_id=? ORDER BY started_at,id", (r,n) -> {
            var ended = r.getObject("completed_at", OffsetDateTime.class);
            return new ExecutionAttempt(r.getObject("id", UUID.class), revisionId, r.getObject("task_id", UUID.class), r.getInt("attempt_number"),
                    ExecutionAttempt.State.valueOf(r.getString("state")), r.getString("executor"), decode(r.getString("input_hashes"), StringList.class).values(),
                    r.getObject("started_at", OffsetDateTime.class).toInstant(), ended == null ? null : ended.toInstant(), r.getString("evidence_location"));
        }, revisionId);
        return new IntelligenceModels.View(requirement, repository, plan.map(a -> decode(a.content(), EngineeringPlan.class)).orElse(null), plan.map(EngineeringArtifact::sha256).orElse(null), invalidated, reused, summaries, attempts);
    }
    @Transactional
    public void recordReuse(UUID revisionId, UUID originalId, UUID copyId, String manifestHash) {
        jdbc.update("INSERT INTO artifact_reuse VALUES (?,?,?,?,?,?)", UUID.randomUUID(), revisionId, originalId, copyId, manifestHash, time());
    }
    @Transactional
    public void denyRepository(UUID revisionId, String selector) {
        jdbc.update("INSERT INTO policy_decisions VALUES (?,?,?,?,?,?,?,?)", UUID.randomUUID(), revisionId,
                "approved-root-snapshot", "1.0", Hashes.sha256(selector), "DENY", "Repository unavailable or violates bounded filesystem policy", time());
    }
    public record StringList(List<String> values) {
        @com.fasterxml.jackson.annotation.JsonCreator(mode=com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING)
        public StringList { values = List.copyOf(values); }
    }
}
