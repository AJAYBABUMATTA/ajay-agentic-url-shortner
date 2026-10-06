package dev.ajaymatta.agentic.engineering;

import dev.ajaymatta.agentic.execution.*;
import dev.ajaymatta.agentic.intelligence.IntelligenceStore;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class EngineeringValidator implements ArtifactValidator {
    private final IntelligenceStore evidence;
    public EngineeringValidator(IntelligenceStore evidence) { this.evidence=evidence; }
    @Override public ValidationResult validate(EngineeringArtifact artifact,ExecutionContext context) {
        if(!artifact.revisionId().equals(context.revisionId()) || !artifact.taskId().equals(context.taskId())
                || !artifact.inputHashes().equals(context.inputHashes()) || !Hashes.sha256(artifact.content()).equals(artifact.sha256())) throw new IllegalArgumentException("Stale engineering artifact");
        var task=evidence.decode(context.inputs().get("task").content(),EngineeringModels.TaskInput.class).task();
        if(artifact.type()==EngineeringArtifact.ArtifactType.FILE_PROPOSAL) {
            var operations=evidence.decode(artifact.content(),EngineeringModels.Proposal.class).operations();
            if(operations.isEmpty()) throw new IllegalArgumentException("Empty engineering proposal");
            var paths=new HashSet<String>();
            for(var operation:operations) {
                if(!paths.add(operation.path()) || !operation.taskId().equals(context.taskId()) || !operation.requirementId().equals(context.revisionId().toString())
                        || !operation.inputHashes().equals(context.inputHashes()) || !operation.criterionIds().equals(task.criterionIds())
                        || !task.impactedPaths().contains(operation.path())) throw new IllegalArgumentException("Proposal not grounded in approved task");
                if(task.role()==AgentRole.TESTING && !operation.path().startsWith("src/test/java/")) throw new IllegalArgumentException("Test agent proposed production mutation");
                if(task.role()==AgentRole.IMPLEMENTATION && operation.path().startsWith("src/test/java/")) throw new IllegalArgumentException("Implementation agent proposed test mutation");
            }
            if(task.role()==AgentRole.REPAIR && paths.stream().anyMatch(p->!p.startsWith("src/main/java/"))) throw new IllegalArgumentException("Repair cannot weaken tests or build configuration");
            if(task.role()!=AgentRole.REPAIR && !paths.equals(new HashSet<>(task.impactedPaths()))) throw new IllegalArgumentException("Incomplete planned file proposal");
        } else if(artifact.type()==EngineeringArtifact.ArtifactType.DIAGNOSIS) {
            var diagnosis=evidence.decode(artifact.content(),EngineeringModels.Diagnosis.class);
            var failure=evidence.decode(context.inputs().get("failure").content(),BuildEvidence.class);
            if(diagnosis.classification()!=failure.classification() || !diagnosis.failedTests().equals(failure.failedTests())
                    || !task.impactedPaths().containsAll(diagnosis.affectedPaths())) throw new IllegalArgumentException("Diagnosis lacks scoped failure evidence");
        } else if(List.of(EngineeringArtifact.ArtifactType.ARCHITECTURE,EngineeringArtifact.ArtifactType.DOCUMENTATION,EngineeringArtifact.ArtifactType.SECURITY_REVIEW).contains(artifact.type())) {
            var decision=evidence.decode(artifact.content(),EngineeringModels.Decision.class);
            if(decision.decision()==null || decision.decision().isBlank() || !decision.criterionIds().equals(task.criterionIds())
                    || !decision.evidenceHashes().equals(context.inputHashes()) || decision.limitations().isEmpty()) throw new IllegalArgumentException("Ungrounded specialist decision");
        } else throw new IllegalArgumentException("Unexpected agent artifact");
        return new ValidationResult(UUID.randomUUID(),artifact.revisionId(),artifact.id(),artifact.sha256(),"engineering-lineage-and-capability-v1",
                ValidationResult.Status.PASSED,"Approved task file scope, criterion lineage and hashes validated","db://engineering_artifacts/"+artifact.id(),evidence.now());
    }
}
