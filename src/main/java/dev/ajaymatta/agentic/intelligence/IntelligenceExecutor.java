package dev.ajaymatta.agentic.intelligence;

import dev.ajaymatta.agentic.execution.*;
import dev.ajaymatta.agentic.workflow.domain.AgentTask;
import dev.ajaymatta.agentic.workflow.domain.TaskState;
import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class IntelligenceExecutor implements AgentExecutor {
    private final Map<AgentRole, Agent> agents = new EnumMap<>(AgentRole.class);
    private final IntelligenceStore store;
    private final IntelligenceValidator validator;
    public IntelligenceExecutor(DeterministicIntelligenceProvider provider, IntelligenceStore store, IntelligenceValidator validator, Clock clock) {
        this.store = store; this.validator = validator;
        agents.put(AgentRole.REQUIREMENT_INTERPRETATION, new IntelligenceAgent(AgentRole.REQUIREMENT_INTERPRETATION, EngineeringArtifact.ArtifactType.REQUIREMENT_ANALYSIS, provider, clock));
        agents.put(AgentRole.AMBIGUITY_ANALYSIS, new IntelligenceAgent(AgentRole.AMBIGUITY_ANALYSIS, EngineeringArtifact.ArtifactType.AMBIGUITY_ANALYSIS, provider, clock));
        agents.put(AgentRole.REPOSITORY_ANALYSIS, new IntelligenceAgent(AgentRole.REPOSITORY_ANALYSIS, EngineeringArtifact.ArtifactType.REPOSITORY_MAP, provider, clock));
        agents.put(AgentRole.PLANNING, new IntelligenceAgent(AgentRole.PLANNING, EngineeringArtifact.ArtifactType.TASK_PLAN, provider, clock));
    }
    @Override public ExecutionAttempt execute(AgentTask task, ExecutionContext context) {
        if (!agents.containsKey(task.role())) throw new IllegalArgumentException("Engineering executor not enabled at this checkpoint");
        var attempt = store.start(task, context);
        try {
            var output = agents.get(task.role()).execute(context);
            if (!output.proposals().isEmpty() || output.artifacts().size() != 1) throw new IllegalArgumentException("Unexpected intelligence output shape");
            var artifact = output.artifacts().getFirst();
            validator.validate(artifact, context);
            TaskState state = task.role() == AgentRole.AMBIGUITY_ANALYSIS
                    && !store.decode(artifact.content(), IntelligenceModels.AmbiguityDecision.class).resolved()
                    ? TaskState.AWAITING_APPROVAL : TaskState.SUCCEEDED;
            return store.finish(context, attempt, artifact, state);
        } catch (RuntimeException failure) { store.fail(context, attempt); throw failure; }
    }
    public EngineeringArtifact executeReuse(AgentTask task, ExecutionContext context, EngineeringArtifact original) {
        var attempt = store.start(task, context);
        try {
            var copy = new EngineeringArtifact(java.util.UUID.randomUUID(), context.revisionId(), task.id(), original.type(),
                    original.schemaVersion(), original.content(), original.sha256(), context.inputHashes(), store.now());
            validator.validate(copy, context);
            store.finish(context, attempt, copy, TaskState.SUCCEEDED);
            store.recordReuse(context.revisionId(), original.id(), copy.id(), store.decode(copy.content(), dev.ajaymatta.agentic.repository.RepositoryMap.class).manifestHash());
            return copy;
        } catch (RuntimeException failure) { store.fail(context, attempt); throw failure; }
    }
}
