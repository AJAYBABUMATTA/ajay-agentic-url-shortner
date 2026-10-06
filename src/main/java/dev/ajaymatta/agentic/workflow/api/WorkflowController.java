package dev.ajaymatta.agentic.workflow.api;

import dev.ajaymatta.agentic.workflow.application.WorkflowService;
import dev.ajaymatta.agentic.intelligence.OperatorAuthorization;
import dev.ajaymatta.agentic.intelligence.RevisionRequests;
import dev.ajaymatta.agentic.intelligence.RevisionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {
    private final WorkflowService service;
    private final RevisionService revisions;
    private final OperatorAuthorization authorization;

    public WorkflowController(WorkflowService service, RevisionService revisions, OperatorAuthorization authorization) {
        this.service = service;
        this.revisions = revisions;
        this.authorization = authorization;
    }

    @PostMapping
    @Operation(summary = "Submit a requirement", description = "Accepts requirement and repositoryPath only. "
            + "Automatic interpretation and planning follow intake. Engineering execution remains gated.")
    public ResponseEntity<WorkflowDetails> submit(@Valid @RequestBody SubmitRequirement request) {
        WorkflowDetails result = service.submit(request);
        return ResponseEntity.accepted().location(URI.create("/api/v1/workflows/" + result.workflow().id())).body(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Inspect persisted workflow, revision, task graph and audit evidence")
    public WorkflowDetails get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/{id}/clarifications")
    @Operation(summary = "Answer current ambiguity questions and create a child revision")
    public ResponseEntity<WorkflowDetails> clarify(@PathVariable UUID id, @Valid @RequestBody RevisionRequests.Clarification request,
            @RequestHeader(value="X-Operator-Id", required=false) String actor,
            @RequestHeader(value="X-Operator-Token", required=false) String token) {
        return ResponseEntity.accepted().body(revisions.clarify(id, request, authorization.authenticate(actor, token)));
    }

    @PostMapping("/{id}/replan")
    @Operation(summary = "Create a new revision after requirement or upstream repository changes")
    public ResponseEntity<WorkflowDetails> replan(@PathVariable UUID id, @Valid @RequestBody RevisionRequests.Replan request,
            @RequestHeader(value="X-Operator-Id", required=false) String actor,
            @RequestHeader(value="X-Operator-Token", required=false) String token) {
        return ResponseEntity.accepted().body(revisions.replan(id, request, authorization.authenticate(actor, token)));
    }
}
