package dev.ajaymatta.agentic.engineering;

import dev.ajaymatta.agentic.execution.*;
import dev.ajaymatta.agentic.intelligence.*;
import dev.ajaymatta.agentic.workflow.domain.*;
import dev.ajaymatta.agentic.workflow.persistence.WorkflowRepository;
import java.util.*;
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
    private final boolean enabled;
    public EngineeringProcessor(EngineeringStore store,IntelligenceStore evidence,WorkflowRepository workflows,EngineeringExecutor executor,
            ProposalTool patches,JdbcTemplate jdbc,@Value("${agentic.processing.enabled:true}") boolean enabled) {
        this.store=store; this.evidence=evidence; this.workflows=workflows; this.executor=executor; this.patches=patches; this.jdbc=jdbc; this.enabled=enabled;
    }
    @Scheduled(fixedDelayString="${agentic.processing.poll-ms:500}")
    public void poll() { if(enabled) for(var revision:store.queued()) process(revision); }
    public void process(UUID revisionId) {
        if(!store.claim(revisionId)) return;
        UUID workflowId=jdbc.queryForObject("SELECT workflow_id FROM workflow_revisions WHERE id=?",UUID.class,revisionId);
        var workflow=workflows.find(workflowId).orElseThrow();
        var revision=workflows.revision(workflowId,workflow.currentRevision());
        try {
            if(!revision.id().equals(revisionId)) throw new IllegalStateException("Stale execution revision");
            var analysis=evidence.view(revisionId);
            var workspace=store.workspace(revisionId);
            var baseline=patches.read(workspace);
            if(!analysis.repository().greenfield() || !analysis.requirement().resolved()
                    || !new HashSet<>(analysis.requirement().criteria().stream().map(RequirementAnalysis.Criterion::capability).toList()).equals(Set.of("create","redirect"))
                    || baseline.keySet().stream().anyMatch(path->!path.endsWith(".md"))) throw new IllegalArgumentException("Generator supports only a Markdown-only greenfield create/redirect baseline");
            if(!ProposalTool.manifest(baseline).equals(workspace.baselineManifestHash())) throw new IllegalStateException("Workspace baseline drift");
            for(var planned:analysis.plan().tasks()) {
                var task=workflows.tasks(revisionId).stream().filter(t->t.key().equals(planned.key())).findFirst().orElseThrow();
                if(task.state()==TaskState.SUCCEEDED) continue;
                var inputs=new TreeMap<String,EngineeringArtifact>();
                for(var artifact:evidence.artifacts(revisionId)) inputs.put(artifact.id().toString(),artifact);
                inputs.put("requirement",evidence.artifact(revisionId,EngineeringArtifact.ArtifactType.REQUIREMENT_ANALYSIS).orElseThrow());
                inputs.put("plan",evidence.artifact(revisionId,EngineeringArtifact.ArtifactType.TASK_PLAN).orElseThrow());
                String content=evidence.encode(new EngineeringModels.TaskInput(planned,baseline));
                var taskInput=new EngineeringArtifact(UUID.randomUUID(),revisionId,task.id(),EngineeringArtifact.ArtifactType.MANIFEST,"engineering-input/1.0",content,
                        Hashes.sha256(content),List.of(revision.requirementHash(),inputs.get("plan").sha256()),evidence.now());
                evidence.persistArtifact(taskInput,"controlled-tool","task-context-v1"); inputs.put("task",taskInput);
                List<String> hashes=new ArrayList<>(List.of(revision.requirementHash()));
                inputs.values().stream().map(EngineeringArtifact::sha256).distinct().sorted().forEach(hashes::add);
                executor.execute(task,new ExecutionContext(workflowId,revisionId,task.id(),revision.requirement(),inputs,hashes));
            }
            store.complete(revisionId,true,"VERIFIED_VERTICAL_SLICE_RELEASE_GATED");
            evidence.status(workflowId,revisionId,WorkflowStatus.AWAITING_RELEASE_APPROVAL);
        } catch(RuntimeException failure) {
            String decision="SAFE_STOP: "+failure.getClass().getSimpleName();
            store.policy(revisionId,revision.requirementHash(),false,decision);
            store.complete(revisionId,false,decision);
            evidence.audit(workflowId,revisionId,null,"ENGINEERING_SAFE_STOP","platform:engineering",decision);
            evidence.status(workflowId,revisionId,WorkflowStatus.SAFE_STOPPED);
        }
    }
}
