package dev.ajaymatta.agentic.intelligence;

import dev.ajaymatta.agentic.execution.*;
import dev.ajaymatta.agentic.planning.EngineeringPlan;
import dev.ajaymatta.agentic.planning.GraphValidator;
import dev.ajaymatta.agentic.repository.RepositoryMap;
import java.util.HashSet;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class IntelligenceValidator implements ArtifactValidator {
    private final IntelligenceStore store;
    private final GraphValidator graphs;
    public IntelligenceValidator(IntelligenceStore store, GraphValidator graphs) { this.store = store; this.graphs = graphs; }
    @Override public ValidationResult validate(EngineeringArtifact artifact, ExecutionContext context) {
        if (!artifact.revisionId().equals(context.revisionId()) || !artifact.taskId().equals(context.taskId())
                || !artifact.inputHashes().equals(context.inputHashes())) throw new IllegalArgumentException("Output lineage mismatch");
        switch (artifact.type()) {
            case REQUIREMENT_ANALYSIS -> {
                var analysis = store.decode(artifact.content(), RequirementAnalysis.class);
                if (analysis.normalizedProblem() == null || analysis.normalizedProblem().isBlank()
                        || analysis.criteria().stream().map(RequirementAnalysis.Criterion::id).distinct().count() != analysis.criteria().size()
                        || analysis.questions().stream().map(RequirementAnalysis.Question::id).distinct().count() != analysis.questions().size()
                        || (!analysis.resolved() && analysis.questions().isEmpty())) throw new IllegalArgumentException("Invalid requirement analysis");
            }
            case AMBIGUITY_ANALYSIS -> {
                var decision = store.decode(artifact.content(), IntelligenceModels.AmbiguityDecision.class);
                var analysis = store.decode(context.inputs().get("requirement").content(), RequirementAnalysis.class);
                if (decision.resolved() != analysis.resolved() || !decision.questions().equals(analysis.questions()) || decision.sourceMutationAllowed()) {
                    throw new IllegalArgumentException("Ambiguity decision bypasses requirement or mutation gate");
                }
            }
            case REPOSITORY_MAP -> {
                var map = store.decode(artifact.content(), RepositoryMap.class);
                var snapshot = store.decode(context.inputs().get("snapshot").content(), IntelligenceModels.SnapshotInput.class);
                if (!map.manifestHash().equals(snapshot.manifestHash()) || map.components().stream().anyMatch(c -> !snapshot.contents().containsKey(c.path()))) {
                    throw new IllegalArgumentException("Repository map is not grounded in current snapshot");
                }
                var paths = new HashSet<>(map.components().stream().map(RepositoryMap.Component::path).toList());
                if (map.dataFlows().stream().anyMatch(f -> !paths.contains(f.fromPath()) || !paths.contains(f.toPath()))) throw new IllegalArgumentException("Ungrounded data flow");
            }
            case TASK_PLAN -> {
                var plan = store.decode(artifact.content(), EngineeringPlan.class);
                var analysis = store.decode(context.inputs().get("requirement").content(), RequirementAnalysis.class);
                var repository = store.decode(context.inputs().get("repository").content(), RepositoryMap.class);
                if (!plan.revisionId().equals(context.revisionId()) || !plan.requirementHash().equals(Hashes.sha256(context.requirement()))
                        || !plan.repositoryHash().equals(repository.manifestHash()) || !graphs.validate(plan.tasks()).equals(plan.executionLayers())) {
                    throw new IllegalArgumentException("Plan input/hash/layer mismatch");
                }
                for (var criterion : analysis.criteria()) if (criterion.behavioral()) {
                    for (AgentRole role : new AgentRole[]{AgentRole.IMPLEMENTATION, AgentRole.TESTING}) {
                        if (plan.tasks().stream().noneMatch(t -> t.role() == role && t.criterionIds().contains(criterion.id()))) throw new IllegalArgumentException("Unplanned behavioral criterion");
                    }
                }
            }
            default -> throw new IllegalArgumentException("Unsupported intelligence artifact");
        }
        return new ValidationResult(UUID.randomUUID(), artifact.revisionId(), artifact.id(), artifact.sha256(),
                "intelligence-schema-and-lineage-v1", ValidationResult.Status.PASSED, "Typed grounded artifact validated",
                "db://engineering_artifacts/" + artifact.id(), store.now());
    }
}
