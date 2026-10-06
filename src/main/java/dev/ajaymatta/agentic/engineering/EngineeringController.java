package dev.ajaymatta.agentic.engineering;

import dev.ajaymatta.agentic.intelligence.OperatorAuthorization;
import dev.ajaymatta.agentic.workflow.application.WorkflowService;
import dev.ajaymatta.agentic.workflow.api.WorkflowDetails;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workflows/{id}")
public class EngineeringController {
    private final ChangeApprovalService approvals;
    private final OperatorAuthorization authorization;
    private final WorkflowService workflows;
    private final EngineeringStore store;
    private final ReleaseApprovalService releases;
    private final ExecutionControlService control;
    public EngineeringController(ChangeApprovalService approvals,OperatorAuthorization authorization,WorkflowService workflows,EngineeringStore store,ReleaseApprovalService releases,ExecutionControlService control) {
        this.approvals=approvals; this.authorization=authorization; this.workflows=workflows; this.store=store;
        this.releases=releases; this.control=control;
    }
    @PostMapping("/release-approvals")
    public ResponseEntity<?> release(@PathVariable UUID id,@Valid @RequestBody ReleaseApprovalService.Request request,
            @RequestHeader(value="X-Operator-Id",required=false) String actor,@RequestHeader(value="X-Operator-Token",required=false) String token) {
        if(!releases.decide(id,request,authorization.authenticate(actor,token))) {
            var problem=org.springframework.http.ProblemDetail.forStatusAndDetail(org.springframework.http.HttpStatus.CONFLICT,"Current release evidence changed; outcome invalidated and baseline restored. Replan required.");
            return ResponseEntity.status(409).body(problem);
        }
        return ResponseEntity.ok(workflows.get(id));
    }
    @PostMapping("/cancel")
    public WorkflowDetails cancel(@PathVariable UUID id,@Valid @RequestBody ExecutionControlService.Request request,
            @RequestHeader(value="X-Operator-Id",required=false) String actor,@RequestHeader(value="X-Operator-Token",required=false) String token) {
        control.request(id,request,authorization.authenticate(actor,token),true); return workflows.get(id);
    }
    @PostMapping("/safe-stop")
    public WorkflowDetails stop(@PathVariable UUID id,@Valid @RequestBody ExecutionControlService.Request request,
            @RequestHeader(value="X-Operator-Id",required=false) String actor,@RequestHeader(value="X-Operator-Token",required=false) String token) {
        control.request(id,request,authorization.authenticate(actor,token),false); return workflows.get(id);
    }
    @PostMapping("/rollback")
    public WorkflowDetails rollback(@PathVariable UUID id,@Valid @RequestBody ExecutionControlService.Request request,
            @RequestHeader(value="X-Operator-Id",required=false) String actor,@RequestHeader(value="X-Operator-Token",required=false) String token) {
        control.rollback(id,request,authorization.authenticate(actor,token)); return workflows.get(id);
    }
    @PostMapping("/change-approvals")
    public ResponseEntity<WorkflowDetails> approve(@PathVariable UUID id,@Valid @RequestBody ChangeApprovalService.Request request,
            @RequestHeader(value="X-Operator-Id",required=false) String actor,@RequestHeader(value="X-Operator-Token",required=false) String token) {
        approvals.decide(id,request,authorization.authenticate(actor,token)); return ResponseEntity.accepted().body(workflows.get(id));
    }
    @GetMapping("/engineering")
    public EngineeringModels.View get(@PathVariable UUID id) {
        var workflow=workflows.get(id); return store.view(workflow.revision().id());
    }
}
