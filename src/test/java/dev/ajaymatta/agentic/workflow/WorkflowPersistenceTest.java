package dev.ajaymatta.agentic.workflow;

import dev.ajaymatta.agentic.workflow.api.SubmitRequirement;
import dev.ajaymatta.agentic.workflow.api.WorkflowDetails;
import dev.ajaymatta.agentic.workflow.application.WorkflowService;
import dev.ajaymatta.agentic.workflow.persistence.WorkflowRepository;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@ActiveProfiles("test")
class WorkflowPersistenceTest {
    @Autowired WorkflowService service;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean WorkflowRepository repository;

    @Test
    void submissionRollsBackAllFourRecordsIfPersistenceFailsAfterWriting() {
        long[] before = counts();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("Simulated post-write failure");
        }).when(repository).insert(any(), any(), any(), any());
        assertThatThrownBy(() -> submit()).isInstanceOf(IllegalStateException.class);
        assertThat(counts()).containsExactly(before);
    }

    @Test
    void taskDependenciesCannotCrossRevisionOrDependOnThemselves() {
        var first = submit();
        var second = submit();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO task_dependencies VALUES (?,?,?)",
                first.revision().id(), first.tasks().getFirst().id(), second.tasks().getFirst().id()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO task_dependencies VALUES (?,?,?)",
                first.revision().id(), first.tasks().getFirst().id(), first.tasks().getFirst().id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void uniqueTaskKeyAndRevisionNumberAreEnforced() {
        var details = submit();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO agent_tasks(id,revision_id,task_key,agent_role,state,"
                        + "entry_gates,exit_gates,max_attempts,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), details.revision().id(), "interpret-requirement", "REQUIREMENT_INTERPRETATION",
                "PENDING", "[]", "[]", 3, OffsetDateTime.now(), OffsetDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO workflow_revisions(id,workflow_id,revision_number,requirement,"
                        + "requirement_hash,repository_path,status,created_at) VALUES (?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), details.workflow().id(), 1, "Duplicate", "a".repeat(64), "fixture", "RECEIVED", OffsetDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void approvalAndValidationMustReferenceExactRevisionAndArtifactHash() {
        var first = submit();
        var other = submit();
        UUID artifact = artifact(first);
        assertThatThrownBy(() -> approval(other.revision().id(), artifact, "a".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> approval(first.revision().id(), artifact, "b".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class);
        approval(first.revision().id(), artifact, "a".repeat(64));
        assertThatThrownBy(() -> jdbc.update("INSERT INTO validation_results VALUES (?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), first.revision().id(), artifact, "b".repeat(64), "test-validator", "PASSED",
                "Passed", "db://test-evidence", OffsetDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void artifactAndParentRevisionCannotBorrowAnotherRevisionOrWorkflow() {
        var first = submit();
        var other = submit();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO engineering_artifacts(id,revision_id,task_id,artifact_type,"
                        + "schema_version,content,sha256,input_hashes,producing_agent,provider,model,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), first.revision().id(), other.tasks().getFirst().id(), "TASK_PLAN", "1", "{}",
                "a".repeat(64), "[]", "PLANNING", "test", "test", OffsetDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO workflow_revisions(id,workflow_id,revision_number,parent_revision_id,"
                        + "requirement,requirement_hash,repository_path,status,created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), first.workflow().id(), 2, other.revision().id(), "Changed", "a".repeat(64),
                "fixture", "RECEIVED", OffsetDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void attemptsAreNumberedUniquelyAndTerminalAttemptsRequireEvidence() {
        var details = submit();
        UUID task = details.tasks().getFirst().id();
        UUID revision = details.revision().id();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("INSERT INTO execution_attempts(id,revision_id,task_id,attempt_number,executor,state,input_hashes,started_at)"
                        + " VALUES (?,?,?,?,?,?,?,?)", UUID.randomUUID(), revision, task, 1, "test", "RUNNING", "[]", now);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO execution_attempts(id,revision_id,task_id,attempt_number,executor,state,input_hashes,started_at)"
                        + " VALUES (?,?,?,?,?,?,?,?)", UUID.randomUUID(), revision, task, 1, "test", "RUNNING", "[]", now))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO execution_attempts(id,revision_id,task_id,attempt_number,executor,state,input_hashes,started_at)"
                        + " VALUES (?,?,?,?,?,?,?,?)", UUID.randomUUID(), revision, task, 2, "test", "SUCCEEDED", "[]", now))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void verifiedRollbackCannotClaimSuccessForADifferentManifest() {
        var details = submit();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO rollback_actions VALUES (?,?,?,?,?,?,?)", UUID.randomUUID(),
                details.revision().id(), "restore", "a".repeat(64), "b".repeat(64), true, OffsetDateTime.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private WorkflowDetails submit() {
        return service.submit(new SubmitRequirement("Create a URL-shortener", "fixture"));
    }

    private UUID artifact(WorkflowDetails details) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO engineering_artifacts(id,revision_id,task_id,artifact_type,schema_version,content,sha256,"
                        + "input_hashes,producing_agent,provider,model,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                id, details.revision().id(), details.tasks().getFirst().id(), "TASK_PLAN", "1", "{}", "a".repeat(64),
                "[]", "PLANNING", "test", "test", OffsetDateTime.now());
        return id;
    }

    private void approval(UUID revision, UUID artifact, String hash) {
        jdbc.update("INSERT INTO approvals(id,revision_id,artifact_id,kind,evidence_hash,actor_id,decision,reason,created_at)"
                        + " VALUES (?,?,?,?,?,?,?,?,?)", UUID.randomUUID(), revision, artifact, "CHANGE", hash,
                "test-operator", "APPROVED", "Reviewed", OffsetDateTime.now());
    }

    private long[] counts() {
        return new long[] {count("workflows"), count("workflow_revisions"), count("agent_tasks"), count("audit_events")};
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }
}
