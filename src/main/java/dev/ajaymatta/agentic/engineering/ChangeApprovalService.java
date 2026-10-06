package dev.ajaymatta.agentic.engineering;

import dev.ajaymatta.agentic.execution.*;
import dev.ajaymatta.agentic.intelligence.IntelligenceStore;
import dev.ajaymatta.agentic.workflow.domain.*;
import dev.ajaymatta.agentic.workflow.persistence.WorkflowRepository;
import jakarta.validation.constraints.*;
import java.time.ZoneOffset;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class ChangeApprovalService {
    private final JdbcTemplate jdbc;
    private final WorkflowRepository workflows;
    private final IntelligenceStore evidence;
    public ChangeApprovalService(JdbcTemplate jdbc,WorkflowRepository workflows,IntelligenceStore evidence) { this.jdbc=jdbc; this.workflows=workflows; this.evidence=evidence; }
    public record Request(@Min(1) int expectedRevision,@NotBlank @Pattern(regexp="[a-f0-9]{64}") String planHash,
                          @NotNull Decision decision,@NotBlank @Size(max=2000) String reason) {}
    public enum Decision { APPROVED, REJECTED }
    @Transactional public void decide(UUID id,Request request,String actor) {
        var numbers=jdbc.query("SELECT current_revision FROM workflows WHERE id=? FOR UPDATE",(r,n)->r.getInt(1),id);
        if(numbers.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var revision=workflows.revision(id,numbers.getFirst());
        if(numbers.getFirst()!=request.expectedRevision() || revision.status()!=WorkflowStatus.AWAITING_CHANGE_APPROVAL) throw new ResponseStatusException(HttpStatus.CONFLICT);
        var plan=evidence.artifact(revision.id(),EngineeringArtifact.ArtifactType.TASK_PLAN).orElseThrow();
        if(!plan.sha256().equals(request.planHash())) throw new ResponseStatusException(HttpStatus.CONFLICT);
        jdbc.update("INSERT INTO approvals VALUES (?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),revision.id(),plan.id(),"CHANGE",plan.sha256(),actor,request.decision().name(),request.reason(),null,evidence.now().atOffset(ZoneOffset.UTC));
        evidence.audit(id,revision.id(),null,"CHANGE_"+request.decision().name(),actor,"planHash="+plan.sha256()+";reason="+request.reason());
        if(request.decision()==Decision.REJECTED) evidence.status(id,revision.id(),WorkflowStatus.SAFE_STOPPED);
        else {
            jdbc.update("INSERT INTO engineering_runs(revision_id,state,created_at) VALUES (?,?,?)",revision.id(),"QUEUED",evidence.now().atOffset(ZoneOffset.UTC));
            evidence.status(id,revision.id(),WorkflowStatus.EXECUTING);
        }
    }
}
