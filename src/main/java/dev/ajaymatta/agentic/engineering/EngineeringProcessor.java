package dev.ajaymatta.agentic.engineering;

import dev.ajaymatta.agentic.execution.*;
import dev.ajaymatta.agentic.intelligence.*;
import dev.ajaymatta.agentic.planning.EngineeringPlan;
import dev.ajaymatta.agentic.workflow.domain.*;
import dev.ajaymatta.agentic.workflow.persistence.WorkflowRepository;
import java.util.*;
import java.time.Duration;
import java.util.concurrent.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class EngineeringProcessor {
    private final EngineeringStore store;
    private final IntelligenceStore evidence;
    private final WorkflowRepository workflows;
    private final EngineeringExecutor executor;
    private final ProposalTool patches;
    private final JdbcTemplate jdbc;
    private final ExecutionControlService control;
    private final RecoveryPolicy recovery;
    private final EngineeringMetrics metrics;
    private final dev.ajaymatta.agentic.repository.RepositoryTools repositories;
    private final boolean enabled;
    private final int parallelism;
    public EngineeringProcessor(EngineeringStore store,IntelligenceStore evidence,WorkflowRepository workflows,EngineeringExecutor executor,
            ProposalTool patches,JdbcTemplate jdbc,ExecutionControlService control,RecoveryPolicy recovery,EngineeringMetrics metrics,dev.ajaymatta.agentic.repository.RepositoryTools repositories,
            @Value("${agentic.processing.enabled:true}") boolean enabled,@Value("${agentic.execution.parallelism:4}") int parallelism) {
        this.store=store; this.evidence=evidence; this.workflows=workflows; this.executor=executor; this.patches=patches; this.jdbc=jdbc;
        this.control=control; this.recovery=recovery; this.metrics=metrics; this.repositories=repositories; this.enabled=enabled; this.parallelism=Math.max(1,Math.min(8,parallelism));
    }
    @Scheduled(fixedDelayString="${agentic.processing.poll-ms:500}")
    public void poll() { if(enabled) for(var revision:store.queued()) process(revision); }
    public void process(UUID revisionId) {
        if(!store.claim(revisionId)) return;
        UUID workflowId=jdbc.queryForObject("SELECT workflow_id FROM workflow_revisions WHERE id=?",UUID.class,revisionId);
        var revision=workflows.revision(workflowId,workflows.find(workflowId).orElseThrow().currentRevision());
        try(var pool=Executors.newFixedThreadPool(parallelism)) {
            if(!revision.id().equals(revisionId)) throw new IllegalStateException("Stale execution revision");
            var analysis=evidence.view(revisionId);
            var workspace=store.workspace(revisionId);
            var baseline=patches.read(workspace);
            var capabilities=new HashSet<>(analysis.requirement().criteria().stream().map(RequirementAnalysis.Criterion::capability).toList());
            boolean green=analysis.repository().greenfield() && capabilities.equals(Set.of("create","redirect")) && baseline.keySet().stream().allMatch(p->p.endsWith(".md"));
            boolean brown=!analysis.repository().greenfield() && !capabilities.isEmpty() && Set.of("analytics-total","analytics-daily").containsAll(capabilities)
                    && capabilities.contains("analytics-total") && baseline.containsKey(BrownfieldSources.ROOT+"TargetApplication.java");
            if(!analysis.requirement().resolved() || !green && !brown || analysis.plan().recoveryScope()==null) throw new IllegalArgumentException("Requirement/repository capability requires human intervention and replanning");
            if(!ProposalTool.manifest(baseline).equals(workspace.baselineManifestHash())) throw new IllegalStateException("Workspace baseline drift");
            if(!repositories.currentManifest(revision.repositoryPath()).equals(workspace.baselineManifestHash())) throw new IllegalStateException("Upstream changed after plan approval; replan required");
            while(true) {
                if(store.stopped(revisionId)) throw new IllegalStateException("Governed stop requested");
                var tasks=workflows.tasks(revisionId); var states=new HashMap<UUID,TaskState>(); tasks.forEach(t->states.put(t.id(),t.state()));
                var pending=analysis.plan().tasks().stream().filter(p->tasks.stream().anyMatch(t->t.key().equals(p.key()) && t.state()==TaskState.PENDING)).toList();
                if(pending.isEmpty()) break;
                var ready=pending.stream().filter(p->p.dependencies().stream().allMatch(key->tasks.stream().anyMatch(t->t.key().equals(key) && t.state()==TaskState.SUCCEEDED))).limit(parallelism).toList();
                if(ready.isEmpty()) throw new IllegalStateException("Dependency graph cannot progress");
                // Build validation and file application have exclusive capabilities. Agent-only ready branches can overlap.
                var futures=new ArrayList<Future<RuntimeException>>();
                for(var planned:ready) futures.add(pool.submit(()-> {
                    try { run(revision,planned,baseline); return null; } catch(RuntimeException failure) { return failure; }
                }));
                RuntimeException failure=null;
                for(var future:futures) {
                    RuntimeException result=future.get();
                    if(result!=null) failure=result;
                }
                if(failure instanceof EngineeringExecutor.BuildStoppedException) repair(revision,baseline);
                else if(failure!=null) throw failure;
                else if(ready.stream().anyMatch(p->p.key().equals("validate-build")) && !store.recovery(revisionId).isEmpty()) {
                    var failed=evidence.view(revisionId).attempts().stream().filter(a->a.state()==ExecutionAttempt.State.FAILED).findFirst().orElseThrow();
                    metrics.recovered(Duration.between(failed.startedAt(),evidence.now()));
                }
            }
            store.awaitHumanRelease(workflowId,revisionId);
        } catch(Exception failure) {
            String reason="SAFE_STOP: "+failure.getClass().getSimpleName();
            store.policy(revisionId,revision.requirementHash(),false,reason);
            control.stop(revision,store.cancelled(revisionId) ? WorkflowStatus.CANCELLED : WorkflowStatus.SAFE_STOPPED,reason);
        }
    }
    private void run(WorkflowRevision revision,EngineeringPlan.PlannedTask planned,Map<String,String> baseline) {
        var task=workflows.tasks(revision.id()).stream().filter(t->t.key().equals(planned.key())).findFirst().orElseThrow();
        executor.execute(task,context(revision,task,planned,baseline));
    }
    private ExecutionContext context(WorkflowRevision revision,AgentTask task,EngineeringPlan.PlannedTask planned,Map<String,String> original) {
        var ancestors=new HashSet<UUID>(); ancestors.add(task.id());
        boolean changed;
        do { changed=false; for(var dependency:workflows.dependencies(revision.id())) if(ancestors.contains(dependency.taskId())) changed |= ancestors.add(dependency.dependsOnTaskId()); } while(changed);
        var inputs=new TreeMap<String,EngineeringArtifact>();
        for(var artifact:evidence.artifacts(revision.id())) if(ancestors.contains(artifact.taskId())) inputs.put(artifact.id().toString(),artifact);
        inputs.put("requirement",evidence.artifact(revision.id(),EngineeringArtifact.ArtifactType.REQUIREMENT_ANALYSIS).orElseThrow());
        inputs.put("plan",evidence.artifact(revision.id(),EngineeringArtifact.ArtifactType.TASK_PLAN).orElseThrow());
        var virtual=new TreeMap<>(original);
        if(task.role()==AgentRole.DIAGNOSIS || task.role()==AgentRole.REPAIR || task.role()==AgentRole.DOCUMENTATION || task.role()==AgentRole.SECURITY_RISK || task.role()==AgentRole.RELEASE_READINESS || task.key().equals("validate-build")) virtual=new TreeMap<>(patches.read(store.workspace(revision.id())));
        else for(var parent:evidence.view(revision.id()).plan().tasks()) {
            UUID parentId=UUID.nameUUIDFromBytes((revision.id()+":"+parent.key()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            if(!parentId.equals(task.id()) && ancestors.contains(parentId)) for(var artifact:evidence.artifacts(revision.id())) if(artifact.taskId().equals(parentId) && artifact.type()==EngineeringArtifact.ArtifactType.FILE_PROPOSAL)
                for(var operation:evidence.decode(artifact.content(),EngineeringModels.Proposal.class).operations()) if(operation.type()==FileOperation.Operation.DELETE) virtual.remove(operation.path()); else virtual.put(operation.path(),operation.content());
        }
        var builds=evidence.artifacts(revision.id()).stream().filter(a->a.type()==EngineeringArtifact.ArtifactType.BUILD_EVIDENCE).toList();
        if(!builds.isEmpty()) { inputs.put("build",builds.getLast()); inputs.put("failure",builds.getLast()); }
        var diagnoses=evidence.artifacts(revision.id()).stream().filter(a->a.type()==EngineeringArtifact.ArtifactType.DIAGNOSIS).toList();
        if(!diagnoses.isEmpty()) inputs.put("diagnosis",diagnoses.getLast());
        String content=evidence.encode(new EngineeringModels.TaskInput(planned,virtual));
        var input=new EngineeringArtifact(UUID.randomUUID(),revision.id(),task.id(),EngineeringArtifact.ArtifactType.MANIFEST,"engineering-input/1.0",content,Hashes.sha256(content),List.of(revision.requirementHash(),inputs.get("plan").sha256()),evidence.now());
        evidence.persistArtifact(input,"controlled-tool","task-context-v2"); inputs.put("task",input);
        var hashes=new ArrayList<String>(List.of(revision.requirementHash())); inputs.values().stream().map(EngineeringArtifact::sha256).distinct().sorted().forEach(hashes::add);
        return new ExecutionContext(revision.workflowId(),revision.id(),task.id(),revision.requirement(),inputs,hashes);
    }
    private void repair(WorkflowRevision revision,Map<String,String> baseline) throws InterruptedException {
        if(store.stopped(revision.id())) throw new IllegalStateException("Stop takes precedence over recovery");
        var buildTask=workflows.tasks(revision.id()).stream().filter(t->t.key().equals("validate-build")).findFirst().orElseThrow();
        var failed=evidence.view(revision.id()).attempts().stream().filter(a->a.taskId().equals(buildTask.id())).reduce((a,b)->b).orElseThrow();
        var failure=evidence.decode(store.latest(revision.id(),EngineeringArtifact.ArtifactType.BUILD_EVIDENCE).content(),BuildEvidence.class);
        var decision=recovery.decide(failed,failure.classification());
        var plannedBuild=evidence.view(revision.id()).plan().tasks().stream().filter(t->t.key().equals("validate-build")).findFirst().orElseThrow();
        var fallback=recovery.decide(context(revision,buildTask,plannedBuild,baseline),failure);
        if(!decision.retry() || fallback.action()!=FallbackStrategy.Action.REPAIR) {
            store.recovery(revision.id(),failed.id(),"HUMAN_INTERVENTION",decision.maximumAttempts(),0,decision.reason(),null); metrics.fallback("human_intervention");
            throw new IllegalStateException(decision.reason());
        }
        var scope=evidence.view(revision.id()).plan().recoveryScope();
        var criteria=evidence.view(revision.id()).requirement().criteria().stream().map(RequirementAnalysis.Criterion::id).toList();
        String diagnosticKey="diagnose-build-"+failed.number(), repairKey="repair-build-"+failed.number();
        var diagnosis=new EngineeringPlan.PlannedTask(diagnosticKey,AgentRole.DIAGNOSIS,List.of("synchronize-proposals"),criteria,scope.productionPaths(),List.of(Gate.DEPENDENCIES_SUCCEEDED),List.of(Gate.ARTIFACT_VALIDATED));
        evidence.task(revision.id(),diagnosticKey,AgentRole.DIAGNOSIS,diagnosis.dependencies()); run(revision,diagnosis,baseline);
        var diagnosed=evidence.decode(store.latest(revision.id(),EngineeringArtifact.ArtifactType.DIAGNOSIS).content(),EngineeringModels.Diagnosis.class);
        if(!diagnosed.repairable()) {
            store.recovery(revision.id(),failed.id(),"RESTORE_BASELINE",decision.maximumAttempts(),0,"No supported evidence-driven production repair",null); metrics.fallback("restore_baseline");
            throw new IllegalStateException("Unsupported diagnosis");
        }
        var repair=new EngineeringPlan.PlannedTask(repairKey,AgentRole.REPAIR,List.of(diagnosticKey),criteria,scope.productionPaths(),List.of(Gate.DEPENDENCIES_SUCCEEDED),List.of(Gate.PATCH_APPLIED));
        var task=evidence.task(revision.id(),repairKey,AgentRole.REPAIR,repair.dependencies());
        String graph=evidence.encode(Map.of("approvedPlanHash",evidence.view(revision.id()).planHash(),"maximumBuildAttempts",scope.maximumBuildAttempts(),"conditionalTasks",List.of(diagnosis,repair),"failedAttempt",failed.id()));
        evidence.persistArtifact(new EngineeringArtifact(UUID.randomUUID(),revision.id(),task.id(),EngineeringArtifact.ArtifactType.TASK_PLAN,"engineering/1.0",graph,Hashes.sha256(graph),List.of(evidence.view(revision.id()).planHash()),evidence.now()),"controlled-tool","conditional-recovery-v1");
        run(revision,repair,baseline);
        var proposal=evidence.artifacts(revision.id()).stream().filter(a->a.taskId().equals(task.id()) && a.type()==EngineeringArtifact.ArtifactType.FILE_PROPOSAL).findFirst().orElseThrow();
        store.recovery(revision.id(),failed.id(),"REPAIR",decision.maximumAttempts(),decision.delay().toMillis(),decision.reason(),proposal.id());
        metrics.retry(failure.classification().name());
        evidence.task(revision.id(),"validate-build",AgentRole.TESTING,List.of(repairKey));
        store.retryTask(buildTask.id());
        Thread.sleep(decision.delay().toMillis());
    }
}
