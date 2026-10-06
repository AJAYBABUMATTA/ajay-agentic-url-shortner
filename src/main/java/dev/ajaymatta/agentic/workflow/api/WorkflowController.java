package dev.ajaymatta.agentic.workflow.api;

import dev.ajaymatta.agentic.workflow.application.WorkflowService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {
    private final WorkflowService service;

    public WorkflowController(WorkflowService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Submit a requirement", description = "Accepts requirement and repositoryPath only. "
            + "Foundation persists a pending interpretation task; execution is not yet enabled.")
    public ResponseEntity<WorkflowDetails> submit(@Valid @RequestBody SubmitRequirement request) {
        WorkflowDetails result = service.submit(request);
        return ResponseEntity.accepted().location(URI.create("/api/v1/workflows/" + result.workflow().id())).body(result);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Inspect persisted workflow, revision, task graph and audit evidence")
    public WorkflowDetails get(@PathVariable UUID id) {
        return service.get(id);
    }
}
