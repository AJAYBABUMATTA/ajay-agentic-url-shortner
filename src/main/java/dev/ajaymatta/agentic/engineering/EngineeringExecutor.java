package dev.ajaymatta.agentic.engineering;

import dev.ajaymatta.agentic.execution.*;
import dev.ajaymatta.agentic.intelligence.*;
import dev.ajaymatta.agentic.workflow.domain.*;
import java.util.*;
import java.time.Duration;
import org.springframework.stereotype.Component;

@Component
public class EngineeringExecutor implements AgentExecutor {
    private final Map<AgentRole,Agent> agents=new EnumMap<>(AgentRole.class);
    private final IntelligenceStore evidence;
    private final EngineeringStore store;
    private final EngineeringValidator validator;
    private final ProposalTool patches;
    private final MavenBuildTool builds;
    private final FeatureCompletionValidator features;
    private final EngineeringMetrics metrics;
    public EngineeringExecutor(DeterministicEngineeringProvider provider,IntelligenceStore evidence,EngineeringStore store,
            EngineeringValidator validator,ProposalTool patches,MavenBuildTool builds,FeatureCompletionValidator features,EngineeringMetrics metrics) {
        this.evidence=evidence; this.store=store; this.validator=validator; this.patches=patches; this.builds=builds; this.features=features; this.metrics=metrics;
        for(var role:List.of(AgentRole.ARCHITECTURE,AgentRole.IMPLEMENTATION,AgentRole.TESTING,AgentRole.DOCUMENTATION,AgentRole.SECURITY_RISK,AgentRole.DIAGNOSIS,AgentRole.REPAIR)) agents.put(role,new EngineeringAgent(role,provider,evidence));
    }
    @Override public ExecutionAttempt execute(AgentTask task,ExecutionContext context) {
        String planHash=context.inputs().get("plan").sha256();
        if(!store.approved(context.revisionId(),planHash)) throw new IllegalStateException("Exact plan approval gate closed");
        if(task.entryGates().contains(Gate.BUILD_AND_TESTS_PASSED)) {
            var build=evidence.decode(store.latest(context.revisionId(),EngineeringArtifact.ArtifactType.BUILD_EVIDENCE).content(),BuildEvidence.class);
            if(build.classification()!=BuildEvidence.FailureClassification.NONE || build.exitCode()!=0 || !build.failedTests().isEmpty()) throw new IllegalStateException("Build entry gate closed");
        }
        var attempt=store.start(task,context); boolean finished=false;
        try {
            var workspace=store.workspace(context.revisionId());
            if(task.key().equals("synchronize-proposals")) {
                var plan=evidence.view(context.revisionId()).plan();
                var before=patches.read(workspace); var operations=new ArrayList<FileOperation>(); StringBuilder diff=new StringBuilder();
                for(var planned:plan.tasks()) {
                    var proposals=evidence.artifacts(context.revisionId()).stream().filter(a->a.type()==EngineeringArtifact.ArtifactType.FILE_PROPOSAL)
                            .filter(a->a.taskId().equals(UUID.nameUUIDFromBytes((context.revisionId()+":"+planned.key()).getBytes(java.nio.charset.StandardCharsets.UTF_8)))).toList();
                    for(var proposal:proposals) {
                        if(store.stopped(context.revisionId())) throw new IllegalStateException("Stop requested before patch batch");
                        var batch=evidence.decode(proposal.content(),EngineeringModels.Proposal.class).operations();
                        var applied=patches.execute(workspace,batch); operations.addAll(batch); diff.append(applied.unifiedDiff());
                    }
                }
                var current=patches.read(workspace); var hashes=new TreeMap<String,String>(); current.forEach((p,c)->hashes.put(p,Hashes.sha256(c)));
                var patch=new EngineeringModels.AppliedPatch(ProposalTool.manifest(before),ProposalTool.manifest(current),hashes,diff.toString(),operations);
                return applied(context,attempt,patch);
            }
            if(task.key().equals("validate-build")) {
                var manifest=store.latest(context.revisionId(),EngineeringArtifact.ArtifactType.MANIFEST);
                var patch=evidence.decode(manifest.content(),EngineeringModels.AppliedPatch.class);
                if(!ProposalTool.manifest(patches.read(workspace)).equals(patch.afterHash())) throw new IllegalStateException("Workspace changed before build");
                var build=builds.executeControlled(workspace,()->store.stopped(context.revisionId())); store.build(attempt,build);
                boolean passed=build.classification()==BuildEvidence.FailureClassification.NONE && build.exitCode()==0 && !build.timedOut()
                        && ProposalTool.manifest(patches.read(workspace)).equals(patch.afterHash());
                var result=store.finish(context,attempt,List.of(artifact(context,EngineeringArtifact.ArtifactType.BUILD_EVIDENCE,evidence.encode(build))),passed,
                        "MAVEN_CLEAN_VERIFY;classification="+build.classification()+";executed="+build.discoveredTests().size()); finished=true;
                store.gate(context.revisionId(),task.id(),Gate.BUILD_AND_TESTS_PASSED.name(),passed,manifest.sha256(),"Fixed clean verify and current manifest checked");
                if(!passed) throw new BuildStoppedException(build.classification().name()); return result;
            }
            if(task.role()==AgentRole.RELEASE_READINESS) {
                var outcome=features.inspect(context.revisionId(),false,null);
                outcome.gates().forEach(g->store.gate(context.revisionId(),task.id(),g.gate(),g.passed(),g.evidenceHash(),g.summary()));
                var result=store.finish(context,attempt,List.of(artifact(context,EngineeringArtifact.ArtifactType.ENGINEERING_OUTCOME,evidence.encode(outcome))),outcome.featureComplete(),outcome.decision());
                finished=true; if(!outcome.featureComplete()) throw new IllegalStateException("Feature completion gate failed");
                store.awaitRelease(task.id()); return result;
            }
            var agent=agents.get(task.role()); if(agent==null) throw new IllegalArgumentException("No engineering agent for role");
            var output=agent.execute(context); output.artifacts().forEach(a->validator.validate(a,context));
            if(!output.proposals().isEmpty()) patches.validateAgainst(workspace,output.proposals(),
                    evidence.decode(context.inputs().get("task").content(),EngineeringModels.TaskInput.class).baseline());
            if(task.role()==AgentRole.REPAIR) {
                var patch=patches.execute(workspace,output.proposals());
                var artifacts=new ArrayList<>(output.artifacts());
                artifacts.add(artifact(context,EngineeringArtifact.ArtifactType.MANIFEST,evidence.encode(patch)));
                artifacts.add(artifact(context,EngineeringArtifact.ArtifactType.UNIFIED_DIFF,patch.unifiedDiff()));
                store.policy(context.revisionId(),artifacts.get(artifacts.size()-2).sha256(),true,"Scoped evidence-driven production repair applied; tests/build configuration untouched");
                return store.finish(context,attempt,artifacts,true,"REPAIR_APPLIED;manifest="+patch.afterHash());
            }
            if(task.role()==AgentRole.SECURITY_RISK) store.policy(context.revisionId(),output.artifacts().getFirst().sha256(),true,"Bounded static capability controls inspected; production hardening remains explicit");
            return store.finish(context,attempt,output.artifacts(),true,"Agent output and current lineage validated; proposals await synchronization");
        } catch(RuntimeException failure) {
            if(!finished) evidence.fail(context,attempt); throw failure;
        } finally { metrics.stage(task.role().name(),Duration.between(attempt.startedAt(),evidence.now())); }
    }
    private ExecutionAttempt applied(ExecutionContext context,ExecutionAttempt attempt,EngineeringModels.AppliedPatch patch) {
        var manifest=artifact(context,EngineeringArtifact.ArtifactType.MANIFEST,evidence.encode(patch));
        var diff=artifact(context,EngineeringArtifact.ArtifactType.UNIFIED_DIFF,patch.unifiedDiff());
        store.policy(context.revisionId(),manifest.sha256(),true,"Exact ordered proposal batches applied; final manifest verified");
        store.gate(context.revisionId(),context.taskId(),Gate.PATCH_APPLIED.name(),true,manifest.sha256(),"Applied exact agent operations in dependency order");
        return store.finish(context,attempt,List.of(manifest,diff),true,"PATCH_APPLIED;manifest="+patch.afterHash());
    }
    private EngineeringArtifact artifact(ExecutionContext context,EngineeringArtifact.ArtifactType type,String content) {
        return new EngineeringArtifact(UUID.randomUUID(),context.revisionId(),context.taskId(),type,"engineering/1.0",content,Hashes.sha256(content),context.inputHashes(),evidence.now());
    }
    public static class BuildStoppedException extends RuntimeException { public BuildStoppedException(String classification) { super(classification); } }
}
