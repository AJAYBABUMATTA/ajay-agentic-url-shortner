package dev.ajaymatta.agentic.intelligence;

import dev.ajaymatta.agentic.execution.*;
import dev.ajaymatta.agentic.planning.EngineeringPlan;
import dev.ajaymatta.agentic.repository.RepositoryMap;
import dev.ajaymatta.agentic.repository.RepositoryTools;
import dev.ajaymatta.agentic.workflow.domain.*;
import dev.ajaymatta.agentic.workflow.persistence.WorkflowRepository;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Durable intake polling; engineering tasks remain gated pending the next checkpoint. */
@Component
public class RequirementProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(RequirementProcessor.class);
    private final IntelligenceStore store;
    private final WorkflowRepository workflows;
    private final IntelligenceExecutor executor;
    private final RepositoryTools tools;
    private final boolean enabled;
    private final dev.ajaymatta.agentic.engineering.WorkerLeases leases;
    public RequirementProcessor(IntelligenceStore store, WorkflowRepository workflows, IntelligenceExecutor executor,
            RepositoryTools tools, @Value("${agentic.processing.enabled:true}") boolean enabled,dev.ajaymatta.agentic.engineering.WorkerLeases leases) {
        this.store = store; this.workflows = workflows; this.executor = executor; this.tools = tools; this.enabled = enabled;
        this.leases=leases;
    }
    @Scheduled(fixedDelayString = "${agentic.processing.poll-ms:500}")
    public void poll() { if (enabled) for (UUID id : store.received()) process(id); }

    public void process(UUID workflowId) {
        var workflow = workflows.find(workflowId).orElseThrow();
        var revision = workflows.revision(workflowId, workflow.currentRevision());
        var ownership=leases.acquire(workflowId,revision.id(),"INTELLIGENCE",false);
        if(ownership.isEmpty()) return;
        try(var lease=ownership.get()) {
        try {
            if (!store.claim(workflowId)) return;
            var interpretation = store.task(revision.id(), "interpret-requirement", AgentRole.REQUIREMENT_INTERPRETATION, List.of());
            Map<String, EngineeringArtifact> humanInputs = new LinkedHashMap<>();
            store.artifacts(revision.id()).stream().filter(a -> a.schemaVersion().equals("clarification/1.0")).findFirst()
                    .ifPresent(a -> humanInputs.put("clarification", a));
            executor.execute(interpretation, context(revision, interpretation, humanInputs));
            var requirement = store.artifact(revision.id(), EngineeringArtifact.ArtifactType.REQUIREMENT_ANALYSIS).orElseThrow();
            var ambiguity = store.task(revision.id(), "analyze-ambiguity", AgentRole.AMBIGUITY_ANALYSIS, List.of("interpret-requirement"));
            executor.execute(ambiguity, context(revision, ambiguity, Map.of("requirement", requirement)));
            if (!store.decode(requirement.content(), RequirementAnalysis.class).resolved()) {
                store.status(workflowId, revision.id(), WorkflowStatus.AWAITING_CLARIFICATION); return;
            }
            store.status(workflowId, revision.id(), WorkflowStatus.PLANNING);
            var repositoryTask = store.task(revision.id(), "analyze-repository", AgentRole.REPOSITORY_ANALYSIS, List.of("analyze-ambiguity"));
            var snapshot = tools.snapshot(revision.repositoryPath(), workflowId, revision.id());
            var manifest = store.snapshot(workflowId, repositoryTask, snapshot, revision.requirementHash());
            var repoContext = context(revision, repositoryTask, Map.of("snapshot", manifest));
            Optional<EngineeringArtifact> previous = revision.parentRevisionId() == null ? Optional.empty()
                    : store.artifact(revision.parentRevisionId(), EngineeringArtifact.ArtifactType.REPOSITORY_MAP)
                    .filter(a -> store.decode(a.content(), RepositoryMap.class).manifestHash().equals(snapshot.workspace().baselineManifestHash()));
            if (previous.isPresent()) executor.executeReuse(repositoryTask, repoContext, previous.get());
            else executor.execute(repositoryTask, repoContext);
            var repository = store.artifact(revision.id(), EngineeringArtifact.ArtifactType.REPOSITORY_MAP).orElseThrow();
            var planning = store.task(revision.id(), "plan-engineering", AgentRole.PLANNING, List.of("analyze-repository"));
            executor.execute(planning, context(revision, planning, Map.of("requirement", requirement, "repository", repository)));
            var plan = store.artifact(revision.id(), EngineeringArtifact.ArtifactType.TASK_PLAN).orElseThrow();
            store.persistPlan(workflowId, store.decode(plan.content(), EngineeringPlan.class));
        } catch (RuntimeException failure) {
            LOG.warn("Requirement processing stopped safely: {}", failure.getClass().getSimpleName());
            if (failure instanceof RepositoryTools.RepositoryPolicyException) store.denyRepository(revision.id(), revision.repositoryPath());
            store.audit(workflowId, revision.id(), null, "PROCESSING_SAFE_STOP", "platform:intelligence", "classification=" + failure.getClass().getSimpleName());
            store.status(workflowId, revision.id(), WorkflowStatus.SAFE_STOPPED);
        }
        }
    }
    private ExecutionContext context(WorkflowRevision revision, AgentTask task, Map<String, EngineeringArtifact> inputs) {
        List<String> hashes = new ArrayList<>(List.of(revision.requirementHash()));
        inputs.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> hashes.add(e.getValue().sha256()));
        return new ExecutionContext(revision.workflowId(), revision.id(), task.id(), revision.requirement(), inputs, hashes);
    }
}
