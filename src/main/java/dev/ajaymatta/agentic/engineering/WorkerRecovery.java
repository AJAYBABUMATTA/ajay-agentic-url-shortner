package dev.ajaymatta.agentic.engineering;

import dev.ajaymatta.agentic.execution.*;
import dev.ajaymatta.agentic.intelligence.IntelligenceStore;
import dev.ajaymatta.agentic.workflow.domain.WorkflowStatus;
import dev.ajaymatta.agentic.workflow.persistence.WorkflowRepository;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Interrupted execution becomes evidenced safe stop. It cannot inherit approval or report an unknown build as passing. */
@Component
public class WorkerRecovery {
    private final WorkerLeases leases; private final WorkflowRepository workflows; private final IntelligenceStore evidence;
    private final EngineeringStore engineering; private final ExecutionControlService control; private final JdbcTemplate jdbc; private final boolean enabled;
    public WorkerRecovery(WorkerLeases leases,WorkflowRepository workflows,IntelligenceStore evidence,EngineeringStore engineering,ExecutionControlService control,JdbcTemplate jdbc,@Value("${agentic.processing.enabled:true}") boolean enabled) {
        this.leases=leases; this.workflows=workflows; this.evidence=evidence; this.engineering=engineering; this.control=control; this.jdbc=jdbc; this.enabled=enabled;
    }
    @Scheduled(fixedDelayString="${agentic.recovery.poll-ms:1000}") public void poll() { if(enabled) for(var owner:leases.expired()) recover(owner); }
    public boolean recover(WorkerLeases.Owner owner) {
        var ownership=leases.acquire(owner.workflowId(),owner.revisionId(),"RECOVERY",true);
        if(ownership.isEmpty()) return false;
        try(var lease=ownership.get()) {
            var workflow=workflows.find(owner.workflowId()).orElseThrow(); var revision=workflows.revision(owner.workflowId(),workflow.currentRevision());
            if(!revision.id().equals(owner.revisionId()) || !Set.of(WorkflowStatus.INTERPRETING,WorkflowStatus.PLANNING,WorkflowStatus.EXECUTING).contains(workflow.status())) return false;
            if(workflow.status()==WorkflowStatus.EXECUTING && jdbc.queryForObject("SELECT COUNT(*) FROM engineering_runs WHERE revision_id=? AND state='RUNNING'",Integer.class,revision.id())==0) return false;
            String reason="Interrupted worker "+owner.ownerId()+"; previous token="+owner.token()+"; execution result is unknown; baseline restoration and fresh review required";
            var running=jdbc.query("SELECT a.id,a.task_id,a.started_at,t.task_key,a.attempt_number,a.input_hashes FROM execution_attempts a JOIN agent_tasks t ON t.id=a.task_id WHERE a.revision_id=? AND a.state='RUNNING'",(r,n)->new Interrupted(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,OffsetDateTime.class).toInstant(),r.getString(4),r.getInt(5),List.of(evidence.decode(r.getString(6),String[].class))),revision.id());
            for(var attempt:running) {
                String location="db://worker_leases/"+revision.id();
                if(attempt.key().equals("validate-build") && jdbc.queryForObject("SELECT COUNT(*) FROM build_evidence WHERE attempt_id=?",Integer.class,attempt.id())==0) {
                    var build=new BuildEvidence(-1,Duration.between(attempt.started(),evidence.now()),false,"",reason,List.of(),List.of(),List.of(),new BuildEvidence.Coverage(false,0,0,null),BuildEvidence.FailureClassification.INFRASTRUCTURE);
                    engineering.build(new ExecutionAttempt(attempt.id(),revision.id(),attempt.task(),attempt.number(),ExecutionAttempt.State.RUNNING,"interrupted-worker",attempt.hashes(),attempt.started(),null,null),build);
                    String content=evidence.encode(build);
                    var artifact=new EngineeringArtifact(UUID.randomUUID(),revision.id(),attempt.task(),EngineeringArtifact.ArtifactType.BUILD_EVIDENCE,"engineering/1.0",content,Hashes.sha256(content),attempt.hashes(),evidence.now());
                    evidence.persistArtifact(artifact,"interrupted-worker-recovery","engineering-v1");
                    location="db://engineering_artifacts/"+artifact.id();
                    jdbc.update("INSERT INTO validation_results VALUES (?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),revision.id(),artifact.id(),artifact.sha256(),"interrupted-worker-result","FAILED",reason,location,evidence.now().atOffset(ZoneOffset.UTC));
                }
                jdbc.update("UPDATE execution_attempts SET state='FAILED',completed_at=?,evidence_location=? WHERE id=? AND state='RUNNING'",evidence.now().atOffset(ZoneOffset.UTC),location,attempt.id());
                engineering.recovery(revision.id(),attempt.id(),"HUMAN_INTERVENTION",1,0,reason,null);
            }
            jdbc.update("UPDATE agent_tasks SET state='FAILED',version=version+1,updated_at=? WHERE revision_id=? AND state='RUNNING'",evidence.now().atOffset(ZoneOffset.UTC),revision.id());
            evidence.audit(owner.workflowId(),revision.id(),null,"WORKER_FAILOVER_RECOVERED","platform:recovery",reason);
            control.stop(revision,engineering.cancelled(revision.id()) ? WorkflowStatus.CANCELLED : WorkflowStatus.SAFE_STOPPED,reason);
            return true;
        }
    }
    private record Interrupted(UUID id,UUID task,Instant started,String key,int number,List<String> hashes) {}
}
