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
    public EngineeringController(ChangeApprovalService approvals,OperatorAuthorization authorization,WorkflowService workflows,EngineeringStore store) {
        this.approvals=approvals; this.authorization=authorization; this.workflows=workflows; this.store=store;
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
