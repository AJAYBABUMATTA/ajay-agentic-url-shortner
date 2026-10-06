package dev.ajaymatta.agentic.engineering;

import dev.ajaymatta.agentic.execution.*;
import dev.ajaymatta.agentic.intelligence.IntelligenceStore;
import dev.ajaymatta.agentic.workflow.domain.*;
import dev.ajaymatta.agentic.workflow.persistence.WorkflowRepository;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class EngineeringStore {
    private final IntelligenceStore evidence;
    private final WorkflowRepository workflows;
    private final JdbcTemplate jdbc;
    public EngineeringStore(IntelligenceStore evidence,WorkflowRepository workflows,JdbcTemplate jdbc) { this.evidence=evidence; this.workflows=workflows; this.jdbc=jdbc; }
    public List<UUID> queued() { return jdbc.query("SELECT revision_id FROM engineering_runs WHERE state='QUEUED' ORDER BY created_at",(r,n)->r.getObject(1,UUID.class)); }
    @Transactional public boolean claim(UUID revision) { return jdbc.update("UPDATE engineering_runs SET state='RUNNING' WHERE revision_id=? AND state='QUEUED'",revision)==1; }
    public RepositoryWorkspace workspace(UUID revision) {
        return jdbc.queryForObject("SELECT * FROM repository_workspaces WHERE revision_id=?",(r,n)->new RepositoryWorkspace(r.getObject("id",UUID.class),revision,
                Path.of(r.getString("repository_location")),Path.of(r.getString("baseline_location")),r.getString("baseline_manifest_hash")),revision);
    }
    @Transactional public ExecutionAttempt start(AgentTask task,ExecutionContext context) {
        var attempt=evidence.start(task,context);
        jdbc.update("UPDATE execution_attempts SET executor='deterministic-engineering-v1' WHERE id=?",attempt.id());
        return new ExecutionAttempt(attempt.id(),attempt.revisionId(),attempt.taskId(),attempt.number(),attempt.state(),"deterministic-engineering-v1",attempt.inputHashes(),attempt.startedAt(),null,null);
    }
    @Transactional public ExecutionAttempt finish(ExecutionContext context,ExecutionAttempt attempt,List<EngineeringArtifact> artifacts,boolean passed,String summary) {
        for(var artifact:artifacts) {
            evidence.persistArtifact(artifact,"controlled-engineering","engineering-v1");
            jdbc.update("INSERT INTO validation_results VALUES (?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),artifact.revisionId(),artifact.id(),artifact.sha256(),
                    "engineering-lineage-and-capability-v1",passed ? "PASSED" : "FAILED",summary,"db://engineering_artifacts/"+artifact.id(),evidence.now().atOffset(ZoneOffset.UTC));
        }
        String location="db://engineering_artifacts/"+artifacts.getLast().id();
        jdbc.update("UPDATE execution_attempts SET state=?,completed_at=?,evidence_location=? WHERE id=? AND state='RUNNING'",passed ? "SUCCEEDED" : "FAILED",evidence.now().atOffset(ZoneOffset.UTC),location,attempt.id());
        jdbc.update("UPDATE agent_tasks SET state=?,updated_at=?,version=version+1 WHERE id=? AND state='RUNNING'",passed ? "SUCCEEDED" : "FAILED",evidence.now().atOffset(ZoneOffset.UTC),attempt.taskId());
        evidence.audit(context.workflowId(),context.revisionId(),attempt.taskId(),passed ? "ENGINEERING_STAGE_VALIDATED" : "ENGINEERING_STAGE_FAILED","platform:engineering",summary);
        return new ExecutionAttempt(attempt.id(),attempt.revisionId(),attempt.taskId(),attempt.number(),passed ? ExecutionAttempt.State.SUCCEEDED : ExecutionAttempt.State.FAILED,
                attempt.executor(),attempt.inputHashes(),attempt.startedAt(),evidence.now(),location);
    }
    public void build(ExecutionAttempt attempt,BuildEvidence build) {
        jdbc.update("INSERT INTO build_evidence VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",attempt.id(),"MAVEN_CLEAN_VERIFY",build.exitCode(),build.duration().toMillis(),build.timedOut(),
                build.stdout(),build.stderr(),build.stdout().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>=60000 || build.stderr().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>=60000,
                evidence.encode(build.compiledProductionPaths()),evidence.encode(build.discoveredTests()),evidence.encode(build.failedTests()),build.coverage().available(),
                build.coverage().coveredLines(),build.coverage().missedLines(),build.coverage().reportLocation(),build.classification().name());
    }
    public void policy(UUID revision,String subject,boolean allowed,String reason) {
        jdbc.update("INSERT INTO policy_decisions VALUES (?,?,?,?,?,?,?,?)",UUID.randomUUID(),revision,"controlled-engineering-proposal","1.0",subject,allowed ? "ALLOW" : "DENY",reason,evidence.now().atOffset(ZoneOffset.UTC));
    }
    public void complete(UUID revision,boolean passed,String decision) {
        jdbc.update("UPDATE engineering_runs SET state=?,completed_at=?,decision=? WHERE revision_id=?",passed ? "SUCCEEDED" : "FAILED",evidence.now().atOffset(ZoneOffset.UTC),decision,revision);
    }
    public boolean approved(UUID revision,String planHash) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM approvals WHERE revision_id=? AND kind='CHANGE' AND decision='APPROVED' AND invalidated_at IS NULL AND evidence_hash=?",Integer.class,revision,planHash)>0;
    }
    public void awaitRelease(UUID task) { jdbc.update("UPDATE agent_tasks SET state='AWAITING_APPROVAL',version=version+1 WHERE id=? AND state='SUCCEEDED'",task); }
    public EngineeringModels.View view(UUID revision) {
        var states=jdbc.query("SELECT state,decision FROM engineering_runs WHERE revision_id=?",(r,n)->new String[]{r.getString(1),r.getString(2)},revision);
        var artifacts=evidence.artifacts(revision).stream().filter(a->a.schemaVersion().equals("engineering/1.0")).toList();
        var outcome=artifacts.stream().filter(a->a.type()==EngineeringArtifact.ArtifactType.ENGINEERING_OUTCOME).reduce((a,b)->b).map(a->evidence.decode(a.content(),EngineeringModels.SliceOutcome.class)).orElse(null);
        RepositoryWorkspace workspace=jdbc.queryForObject("SELECT COUNT(*) FROM repository_workspaces WHERE revision_id=?",Integer.class,revision)==0 ? null : workspace(revision);
        return new EngineeringModels.View(states.isEmpty()?"NOT_STARTED":states.getFirst()[0],states.isEmpty()?null:states.getFirst()[1],workspace,artifacts,outcome);
    }
}
