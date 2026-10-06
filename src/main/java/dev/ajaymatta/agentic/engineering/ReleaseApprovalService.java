package dev.ajaymatta.agentic.engineering;

import dev.ajaymatta.agentic.execution.*;
import dev.ajaymatta.agentic.intelligence.IntelligenceStore;
import dev.ajaymatta.agentic.workflow.domain.*;
import dev.ajaymatta.agentic.workflow.persistence.WorkflowRepository;
import jakarta.validation.constraints.*;
import java.time.ZoneOffset;
import java.time.Duration;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReleaseApprovalService {
    private final JdbcTemplate jdbc;
    private final WorkflowRepository workflows;
    private final IntelligenceStore evidence;
    private final EngineeringStore store;
    private final FeatureCompletionValidator features;
    private final ExecutionControlService control;
    private final EngineeringMetrics metrics;
    public ReleaseApprovalService(JdbcTemplate jdbc,WorkflowRepository workflows,IntelligenceStore evidence,EngineeringStore store,
            FeatureCompletionValidator features,ExecutionControlService control,EngineeringMetrics metrics) {
        this.jdbc=jdbc; this.workflows=workflows; this.evidence=evidence; this.store=store; this.features=features; this.control=control; this.metrics=metrics;
    }
    public record Request(@Min(1) int expectedRevision,@NotBlank @Pattern(regexp="[a-f0-9]{64}") String outcomeHash,
                          @NotNull ChangeApprovalService.Decision decision,@NotBlank @Size(max=2000) String reason) {}
    @Transactional public boolean decide(UUID id,Request request,String actor) {
        var numbers=jdbc.query("SELECT current_revision FROM workflows WHERE id=? FOR UPDATE",(r,n)->r.getInt(1),id);
        if(numbers.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var revision=workflows.revision(id,numbers.getFirst());
        if(numbers.getFirst()!=request.expectedRevision() || revision.status()!=WorkflowStatus.AWAITING_RELEASE_APPROVAL) throw new ResponseStatusException(HttpStatus.CONFLICT);
        var candidate=store.latest(revision.id(),EngineeringArtifact.ArtifactType.ENGINEERING_OUTCOME);
        if(!candidate.sha256().equals(request.outcomeHash())) throw new ResponseStatusException(HttpStatus.CONFLICT);
        var previous=evidence.decode(candidate.content(),EngineeringModels.SliceOutcome.class);
        var current=features.inspect(revision.id(),false,null);
        if(!previous.featureComplete() || !current.featureComplete() || !previous.manifestHash().equals(current.manifestHash())
                || !previous.planHash().equals(current.planHash()) || !previous.artifactHashes().equals(current.artifactHashes())) {
            evidence.audit(id,revision.id(),candidate.taskId(),"RELEASE_EVIDENCE_INVALIDATED",actor,"Candidate drift; hash="+candidate.sha256());
            control.stop(revision,WorkflowStatus.SAFE_STOPPED,"Release evidence changed; replan required");
            return false;
        }
        UUID approvalId=UUID.randomUUID();
        jdbc.update("INSERT INTO approvals VALUES (?,?,?,?,?,?,?,?,?,?)",approvalId,revision.id(),candidate.id(),"RELEASE",candidate.sha256(),actor,request.decision().name(),request.reason(),null,evidence.now().atOffset(ZoneOffset.UTC));
        evidence.audit(id,revision.id(),candidate.taskId(),"RELEASE_"+request.decision().name(),actor,"outcomeHash="+candidate.sha256()+";reason="+request.reason());
        if(request.decision()==ChangeApprovalService.Decision.REJECTED) {
            control.stop(revision,WorkflowStatus.SAFE_STOPPED,"Human rejected exact engineering outcome"); return true;
        }
        var outcome=features.inspect(revision.id(),true,candidate.sha256());
        if(!outcome.releaseReady()) throw new IllegalStateException("Release gate closed");
        String content=evidence.encode(outcome);
        var finalArtifact=new EngineeringArtifact(UUID.randomUUID(),revision.id(),candidate.taskId(),EngineeringArtifact.ArtifactType.ENGINEERING_OUTCOME,"engineering/1.0",content,Hashes.sha256(content),List.of(candidate.sha256(),Hashes.sha256(approvalId.toString())),evidence.now());
        evidence.persistArtifact(finalArtifact,"controlled-engineering","release-gates-v1");
        jdbc.update("INSERT INTO validation_results VALUES (?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),revision.id(),finalArtifact.id(),finalArtifact.sha256(),"feature-completion-ten-gates-v1","PASSED","All ten current evidence gates passed","db://engineering_artifacts/"+finalArtifact.id(),evidence.now().atOffset(ZoneOffset.UTC));
        outcome.gates().forEach(g->store.gate(revision.id(),candidate.taskId(),g.gate(),g.passed(),g.evidenceHash(),g.summary()));
        jdbc.update("UPDATE agent_tasks SET state='SUCCEEDED',version=version+1,updated_at=? WHERE id=? AND state='AWAITING_APPROVAL'",evidence.now().atOffset(ZoneOffset.UTC),candidate.taskId());
        store.complete(revision.id(),true,"RELEASE_READY"); evidence.status(id,revision.id(),WorkflowStatus.RELEASE_READY);
        evidence.audit(id,revision.id(),candidate.taskId(),"FINAL_ENGINEERING_OUTCOME", "platform:release", "hash="+finalArtifact.sha256()+";approvedCandidate="+candidate.sha256());
        metrics.outcome("release_ready",Duration.between(workflows.find(id).orElseThrow().createdAt(),evidence.now()));
        return true;
    }
}