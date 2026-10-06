package dev.ajaymatta.agentic.workflow.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ajaymatta.agentic.execution.AgentRole;
import dev.ajaymatta.agentic.governance.AuditEvent;
import dev.ajaymatta.agentic.workflow.domain.AgentTask;
import dev.ajaymatta.agentic.workflow.domain.Gate;
import dev.ajaymatta.agentic.workflow.domain.TaskDependency;
import dev.ajaymatta.agentic.workflow.domain.TaskState;
import dev.ajaymatta.agentic.workflow.domain.Workflow;
import dev.ajaymatta.agentic.workflow.domain.WorkflowRevision;
import dev.ajaymatta.agentic.workflow.domain.WorkflowStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorkflowRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public WorkflowRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public void insert(Workflow workflow, WorkflowRevision revision, AgentTask task, AuditEvent event) {
        jdbc.update("INSERT INTO workflows(id,current_revision,status,version,created_at,updated_at) VALUES (?,?,?,?,?,?)",
                workflow.id(), workflow.currentRevision(), workflow.status().name(), workflow.version(),
                utc(workflow.createdAt()), utc(workflow.updatedAt()));
        jdbc.update("INSERT INTO workflow_revisions(id,workflow_id,revision_number,parent_revision_id,requirement,"
                        + "requirement_hash,repository_path,status,created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                revision.id(), revision.workflowId(), revision.number(), revision.parentRevisionId(), revision.requirement(),
                revision.requirementHash(), revision.repositoryPath(), revision.status().name(), utc(revision.createdAt()));
        jdbc.update("INSERT INTO agent_tasks(id,revision_id,task_key,agent_role,state,entry_gates,exit_gates,"
                        + "attempt_count,max_attempts,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                task.id(), task.revisionId(), task.key(), task.role().name(), task.state().name(),
                gatesJson(task.entryGates()), gatesJson(task.exitGates()), task.attemptCount(), task.maxAttempts(),
                utc(workflow.createdAt()), utc(workflow.createdAt()));
        jdbc.update("INSERT INTO audit_events(id,workflow_id,revision_id,task_id,event_type,actor_id,details,created_at)"
                        + " VALUES (?,?,?,?,?,?,?,?)", event.id(), event.workflowId(), event.revisionId(), event.taskId(),
                event.eventType(), event.actorId(), event.details(), utc(event.createdAt()));
    }

    public Optional<Workflow> find(UUID id) {
        return jdbc.query("SELECT * FROM workflows WHERE id=?", (rs, row) -> new Workflow(
                uuid(rs, "id"), rs.getInt("current_revision"), WorkflowStatus.valueOf(rs.getString("status")),
                rs.getLong("version"), instant(rs, "created_at"), instant(rs, "updated_at")), id).stream().findFirst();
    }

    public WorkflowRevision revision(UUID workflowId, int number) {
        return jdbc.queryForObject("SELECT * FROM workflow_revisions WHERE workflow_id=? AND revision_number=?",
                (rs, row) -> new WorkflowRevision(uuid(rs, "id"), uuid(rs, "workflow_id"), rs.getInt("revision_number"),
                        uuid(rs, "parent_revision_id"), rs.getString("requirement"), rs.getString("requirement_hash"),
                        rs.getString("repository_path"), WorkflowStatus.valueOf(rs.getString("status")),
                        instant(rs, "created_at")), workflowId, number);
    }

    public List<AgentTask> tasks(UUID revisionId) {
        return jdbc.query("SELECT * FROM agent_tasks WHERE revision_id=? ORDER BY task_key", (rs, row) -> new AgentTask(
                uuid(rs, "id"), uuid(rs, "revision_id"), rs.getString("task_key"),
                AgentRole.valueOf(rs.getString("agent_role")), TaskState.valueOf(rs.getString("state")),
                gates(rs.getString("entry_gates")), gates(rs.getString("exit_gates")),
                rs.getInt("attempt_count"), rs.getInt("max_attempts")), revisionId);
    }

    public List<TaskDependency> dependencies(UUID revisionId) {
        return jdbc.query("SELECT * FROM task_dependencies WHERE revision_id=? ORDER BY task_id,depends_on_task_id",
                (rs, row) -> new TaskDependency(uuid(rs, "revision_id"), uuid(rs, "task_id"),
                        uuid(rs, "depends_on_task_id")), revisionId);
    }

    public List<AuditEvent> audit(UUID workflowId) {
        return jdbc.query("SELECT * FROM audit_events WHERE workflow_id=? ORDER BY created_at,id", (rs, row) -> new AuditEvent(
                uuid(rs, "id"), uuid(rs, "workflow_id"), uuid(rs, "revision_id"), uuid(rs, "task_id"),
                rs.getString("event_type"), rs.getString("actor_id"), rs.getString("details"), instant(rs, "created_at")), workflowId);
    }

    private String gatesJson(List<Gate> values) {
        try {
            return json.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new DataRetrievalFailureException("Cannot serialize task gates", exception);
        }
    }

    private List<Gate> gates(String values) {
        try {
            return json.readValue(values, new TypeReference<>() {});
        } catch (JsonProcessingException exception) {
            throw new DataRetrievalFailureException("Invalid stored task gates", exception);
        }
    }

    private static OffsetDateTime utc(Instant value) {
        return value.atOffset(ZoneOffset.UTC);
    }

    private static UUID uuid(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, UUID.class);
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        return rs.getObject(column, OffsetDateTime.class).toInstant();
    }
}
