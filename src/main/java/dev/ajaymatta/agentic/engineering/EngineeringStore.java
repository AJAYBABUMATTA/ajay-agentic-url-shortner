package dev.ajaymatta.agentic.engineering;

import dev.ajaymatta.agentic.execution.*;
import dev.ajaymatta.agentic.intelligence.IntelligenceStore;
import dev.ajaymatta.agentic.workflow.domain.*;
import dev.ajaymatta.agentic.workflow.persistence.WorkflowRepository;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import dev.ajaymatta.agentic.governance.*;
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
        if(stopped(task.revisionId())) throw new IllegalStateException("Governed stop requested");
        var attempt=evidence.start(task,context);
        gate(task.revisionId(),task.id(),"CURRENT_INPUT_HASHES_AND_DEPENDENCIES",true,Hashes.sha256(evidence.encode(context.inputHashes())),"Current revision and dependencies verified before attempt claim");
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
        gate(context.revisionId(),attempt.taskId(),"ARTIFACT_VALIDATED",passed,artifacts.getLast().sha256(),summary);
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
    @Transactional public void awaitHumanRelease(UUID workflow,UUID revision) {
        jdbc.queryForObject("SELECT current_revision FROM workflows WHERE id=? FOR UPDATE",Integer.class,workflow);
        if(stopped(revision)) throw new IllegalStateException("Governed stop precedes finalization");
        complete(revision,true,"FEATURE_COMPLETE_RELEASE_GATED");
        evidence.status(workflow,revision,WorkflowStatus.AWAITING_RELEASE_APPROVAL);
    }
    public boolean approved(UUID revision,String planHash) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM approvals WHERE revision_id=? AND kind='CHANGE' AND decision='APPROVED' AND invalidated_at IS NULL AND evidence_hash=?",Integer.class,revision,planHash)>0;
    }
    public void awaitRelease(UUID task) { jdbc.update("UPDATE agent_tasks SET state='AWAITING_APPROVAL',version=version+1 WHERE id=? AND state='SUCCEEDED'",task); }
    public boolean stopped(UUID revision) { return jdbc.queryForObject("SELECT COUNT(*) FROM engineering_runs WHERE revision_id=? AND (stop_requested=TRUE OR cancel_requested=TRUE)",Integer.class,revision)>0; }
    public boolean cancelled(UUID revision) { return jdbc.queryForObject("SELECT COUNT(*) FROM engineering_runs WHERE revision_id=? AND cancel_requested=TRUE",Integer.class,revision)>0; }
    public void gate(UUID revision,UUID task,String name,boolean passed,String hash,String summary) {
        jdbc.update("INSERT INTO execution_gates VALUES (?,?,?,?,?,?,?,?)",UUID.randomUUID(),revision,task,name,passed,hash,summary,evidence.now().atOffset(ZoneOffset.UTC));
    }
    public List<EngineeringModels.GateCheck> gates(UUID revision) { return jdbc.query("SELECT * FROM execution_gates WHERE revision_id=? ORDER BY created_at,id",(r,n)->new EngineeringModels.GateCheck(r.getString("gate"),r.getBoolean("passed"),r.getString("evidence_hash"),r.getString("summary")),revision); }
    public EngineeringArtifact latest(UUID revision,EngineeringArtifact.ArtifactType type) {
        return evidence.artifacts(revision).stream().filter(a->a.type()==type && a.schemaVersion().equals("engineering/1.0")).reduce((a,b)->b).orElseThrow();
    }
    public boolean validated(EngineeringArtifact artifact) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM validation_results WHERE artifact_id=? AND artifact_hash=? AND status='PASSED'",Integer.class,artifact.id(),artifact.sha256())>0;
    }
    public List<Approval> approvals(UUID revision) {
        return jdbc.query("SELECT * FROM approvals WHERE revision_id=? AND invalidated_at IS NULL ORDER BY created_at,id",(r,n)->new Approval(r.getObject("id",UUID.class),revision,r.getObject("artifact_id",UUID.class),
                Approval.Kind.valueOf(r.getString("kind")),r.getString("evidence_hash"),r.getString("actor_id"),Approval.Decision.valueOf(r.getString("decision")),r.getString("reason"),r.getObject("created_at",OffsetDateTime.class).toInstant()),revision);
    }
    public List<PolicyDecision> policies(UUID revision) {
        return jdbc.query("SELECT * FROM policy_decisions WHERE revision_id=? ORDER BY created_at,id",(r,n)->new PolicyDecision(r.getObject("id",UUID.class),revision,r.getString("policy"),r.getString("policy_version"),r.getString("subject_hash"),
                PolicyDecision.Verdict.valueOf(r.getString("verdict")),r.getString("reason"),r.getObject("created_at",OffsetDateTime.class).toInstant()),revision);
    }
    public List<EngineeringModels.RecoveryEvidence> recovery(UUID revision) {
        return jdbc.query("SELECT * FROM recovery_decisions WHERE revision_id=? ORDER BY created_at,id",(r,n)->new EngineeringModels.RecoveryEvidence(r.getObject("id",UUID.class),r.getObject("attempt_id",UUID.class),r.getString("action"),r.getInt("maximum_attempts"),r.getLong("delay_ms"),r.getString("reason"),r.getObject("repair_artifact_id",UUID.class),r.getObject("created_at",OffsetDateTime.class).toInstant()),revision);
    }
    public List<RollbackAction> rollbacks(UUID revision) {
        return jdbc.query("SELECT * FROM rollback_actions WHERE revision_id=? ORDER BY created_at,id",(r,n)->new RollbackAction(r.getObject("id",UUID.class),revision,r.getString("reason"),r.getString("expected_manifest_hash"),r.getString("restored_manifest_hash"),r.getBoolean("verified"),r.getObject("created_at",OffsetDateTime.class).toInstant()),revision);
    }
    public void recovery(UUID revision,UUID attempt,String action,int bound,long delay,String reason,UUID repair) {
        jdbc.update("INSERT INTO recovery_decisions VALUES (?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),revision,attempt,action,bound,delay,reason,repair,evidence.now().atOffset(ZoneOffset.UTC));
    }
    public void rollback(UUID revision,String reason,String expected,String actual,boolean verified) {
        jdbc.update("INSERT INTO rollback_actions VALUES (?,?,?,?,?,?,?)",UUID.randomUUID(),revision,reason,expected,actual,verified,evidence.now().atOffset(ZoneOffset.UTC));
    }
    @Transactional public void retryTask(UUID task) {
        if(jdbc.update("UPDATE agent_tasks SET state='PENDING',updated_at=?,version=version+1 WHERE id=? AND state='FAILED' AND attempt_count<max_attempts",evidence.now().atOffset(ZoneOffset.UTC),task)!=1) throw new IllegalStateException("Retry bound or state conflict");
    }
    @Transactional public void invalidateForStop(UUID revision) {
        var now=evidence.now().atOffset(ZoneOffset.UTC);
        jdbc.update("UPDATE approvals SET invalidated_at=? WHERE revision_id=? AND kind='RELEASE' AND decision='APPROVED' AND invalidated_at IS NULL",now,revision);
        jdbc.update("UPDATE engineering_artifacts SET invalidated_at=? WHERE revision_id=? AND artifact_type='ENGINEERING_OUTCOME' AND invalidated_at IS NULL",now,revision);
        jdbc.update("UPDATE agent_tasks SET state='CANCELLED',updated_at=?,version=version+1 WHERE revision_id=? AND state IN ('PENDING','READY','AWAITING_APPROVAL')",now,revision);
    }
    public EngineeringModels.View view(UUID revision) {
        var states=jdbc.query("SELECT state,decision FROM engineering_runs WHERE revision_id=?",(r,n)->new String[]{r.getString(1),r.getString(2)},revision);
        var artifacts=evidence.artifacts(revision).stream().filter(a->a.schemaVersion().equals("engineering/1.0")).toList();
        var outcome=artifacts.stream().filter(a->a.type()==EngineeringArtifact.ArtifactType.ENGINEERING_OUTCOME).reduce((a,b)->b).map(a->evidence.decode(a.content(),EngineeringModels.SliceOutcome.class)).orElse(null);
        RepositoryWorkspace workspace=jdbc.queryForObject("SELECT COUNT(*) FROM repository_workspaces WHERE revision_id=?",Integer.class,revision)==0 ? null : workspace(revision);
        return new EngineeringModels.View(states.isEmpty()?"NOT_STARTED":states.getFirst()[0],states.isEmpty()?null:states.getFirst()[1],workspace,artifacts,outcome,approvals(revision),policies(revision),recovery(revision),rollbacks(revision),gates(revision));
    }
}
