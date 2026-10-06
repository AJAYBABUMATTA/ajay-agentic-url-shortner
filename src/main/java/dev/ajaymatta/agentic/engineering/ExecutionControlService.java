package dev.ajaymatta.agentic.engineering;

import dev.ajaymatta.agentic.intelligence.IntelligenceStore;
import dev.ajaymatta.agentic.workflow.domain.*;
import dev.ajaymatta.agentic.workflow.persistence.WorkflowRepository;
import jakarta.validation.constraints.*;
import java.util.*;
import java.time.Duration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExecutionControlService {
    private final JdbcTemplate jdbc;
    private final WorkflowRepository workflows;
    private final IntelligenceStore evidence;
    private final EngineeringStore store;
    private final BaselineRollbackTool rollback;
    private final EngineeringMetrics metrics;
    public ExecutionControlService(JdbcTemplate jdbc,WorkflowRepository workflows,IntelligenceStore evidence,EngineeringStore store,BaselineRollbackTool rollback,EngineeringMetrics metrics) {
        this.jdbc=jdbc; this.workflows=workflows; this.evidence=evidence; this.store=store; this.rollback=rollback; this.metrics=metrics;
    }
    public record Request(@Min(1) int expectedRevision,@NotBlank @Size(max=2000) String reason) {}
    @Transactional public void request(UUID workflowId,Request request,String actor,boolean cancel) {
        var revision=lock(workflowId,request.expectedRevision());
        if(revision.status()==WorkflowStatus.EXECUTING) {
            jdbc.update("UPDATE engineering_runs SET stop_requested=TRUE,cancel_requested=(cancel_requested OR ?),stop_reason=? WHERE revision_id=?",cancel,request.reason(),revision.id());
            evidence.audit(workflowId,revision.id(),null,cancel ? "CANCELLATION_REQUESTED" : "SAFE_STOP_REQUESTED",actor,request.reason());
        } else if(List.of(WorkflowStatus.AWAITING_CHANGE_APPROVAL,WorkflowStatus.AWAITING_RELEASE_APPROVAL,WorkflowStatus.RELEASE_READY,WorkflowStatus.AWAITING_CLARIFICATION).contains(revision.status())) {
            stop(revision,cancel ? WorkflowStatus.CANCELLED : WorkflowStatus.SAFE_STOPPED,request.reason());
            evidence.audit(workflowId,revision.id(),null,"HUMAN_STOP",actor,request.reason());
        } else throw new ResponseStatusException(HttpStatus.CONFLICT);
    }
    @Transactional public void rollback(UUID workflowId,Request request,String actor) {
        var revision=lock(workflowId,request.expectedRevision());
        if(!List.of(WorkflowStatus.AWAITING_RELEASE_APPROVAL,WorkflowStatus.RELEASE_READY,WorkflowStatus.SAFE_STOPPED).contains(revision.status())) throw new ResponseStatusException(HttpStatus.CONFLICT);
        stop(revision,WorkflowStatus.ROLLED_BACK,request.reason()); evidence.audit(workflowId,revision.id(),null,"HUMAN_ROLLBACK",actor,request.reason());
    }
    public void stop(WorkflowRevision revision,WorkflowStatus terminal,String reason) {
        boolean hasWorkspace=jdbc.queryForObject("SELECT COUNT(*) FROM repository_workspaces WHERE revision_id=?",Integer.class,revision.id())>0;
        boolean restored=true;
        if(hasWorkspace) {
            var workspace=store.workspace(revision.id());
            String actual=null;
            try {
                var criteria=evidence.view(revision.id()).requirement().criteria().stream().map(c->c.id()).toList();
                var action=rollback.execute(workspace,criteria.isEmpty()?List.of("GOVERNED-STOP"):criteria);
                actual=action.restoredManifestHash(); restored=action.verified();
            } catch(RuntimeException failure) { restored=false; }
            store.rollback(revision.id(),reason,workspace.baselineManifestHash(),actual,restored); metrics.rollback(restored);
            evidence.audit(revision.workflowId(),revision.id(),null,"BASELINE_RESTORED","platform:rollback","verified="+restored+";expected="+workspace.baselineManifestHash()+";actual="+actual);
        }
        store.invalidateForStop(revision.id());
        store.complete(revision.id(),false,reason+";baselineRestored="+restored);
        evidence.status(revision.workflowId(),revision.id(),restored ? terminal : WorkflowStatus.FAILED);
        metrics.outcome(restored ? terminal.name().toLowerCase(Locale.ROOT) : "failed",Duration.between(workflows.find(revision.workflowId()).orElseThrow().createdAt(),evidence.now()));
    }
    private WorkflowRevision lock(UUID id,int expected) {
        var rows=jdbc.query("SELECT current_revision FROM workflows WHERE id=? FOR UPDATE",(r,n)->r.getInt(1),id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if(rows.getFirst()!=expected) throw new ResponseStatusException(HttpStatus.CONFLICT);
        return workflows.revision(id,expected);
    }
}
