package dev.ajaymatta.agentic.engineering;

import dev.ajaymatta.agentic.execution.*;
import dev.ajaymatta.agentic.intelligence.*;
import dev.ajaymatta.agentic.workflow.domain.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class EngineeringExecutor implements AgentExecutor {
    private final Map<AgentRole,Agent> agents=new EnumMap<>(AgentRole.class);
    private final IntelligenceStore evidence;
    private final EngineeringStore store;
    private final EngineeringValidator validator;
    private final ProposalTool patches;
    private final MavenBuildTool builds;
    public EngineeringExecutor(DeterministicEngineeringProvider provider,IntelligenceStore evidence,EngineeringStore store,
            EngineeringValidator validator,ProposalTool patches,MavenBuildTool builds) {
        this.evidence=evidence; this.store=store; this.validator=validator; this.patches=patches; this.builds=builds;
        for(var role:List.of(AgentRole.ARCHITECTURE,AgentRole.IMPLEMENTATION,AgentRole.TESTING,AgentRole.DOCUMENTATION,AgentRole.SECURITY_RISK)) agents.put(role,new EngineeringAgent(role,provider,evidence));
    }
    @Override public ExecutionAttempt execute(AgentTask task,ExecutionContext context) {
        var attempt=store.start(task,context);
        boolean finished=false;
        try {
            var workspace=store.workspace(context.revisionId());
            String planHash=context.inputs().get("plan").sha256();
            if(!store.approved(context.revisionId(),planHash)) throw new IllegalStateException("Exact plan approval gate closed");
            if(task.key().equals("synchronize-proposals")) {
                List<FileOperation> operations=evidence.artifacts(context.revisionId()).stream().filter(a->a.type()==EngineeringArtifact.ArtifactType.FILE_PROPOSAL)
                        .flatMap(a->evidence.decode(a.content(),EngineeringModels.Proposal.class).operations().stream()).toList();
                patches.validate(workspace,operations);
                var patch=patches.execute(workspace,operations);
                var manifest=artifact(context,EngineeringArtifact.ArtifactType.MANIFEST,evidence.encode(patch));
                var diff=artifact(context,EngineeringArtifact.ArtifactType.UNIFIED_DIFF,patch.unifiedDiff());
                store.policy(context.revisionId(),manifest.sha256(),true,"Exact validated proposals applied atomically per file; final manifest verified");
                return store.finish(context,attempt,List.of(manifest,diff),true,"PATCH_APPLIED;manifest="+patch.afterHash());
            }
            if(task.key().equals("validate-build")) {
                var applied=evidence.artifacts(context.revisionId()).stream().filter(a->a.type()==EngineeringArtifact.ArtifactType.MANIFEST && a.schemaVersion().equals("engineering/1.0")).findFirst().orElseThrow();
                var patch=evidence.decode(applied.content(),EngineeringModels.AppliedPatch.class);
                if(!ProposalTool.manifest(patches.read(workspace)).equals(patch.afterHash())) throw new IllegalStateException("Workspace changed after proposal application");
                var build=builds.execute(workspace,null);
                store.build(attempt,build);
                boolean passed=build.classification()==BuildEvidence.FailureClassification.NONE && !build.timedOut() && build.exitCode()==0
                        && ProposalTool.manifest(patches.read(workspace)).equals(patch.afterHash());
                var artifact=artifact(context,EngineeringArtifact.ArtifactType.BUILD_EVIDENCE,evidence.encode(build));
                var completed=store.finish(context,attempt,List.of(artifact),passed,"MAVEN_CLEAN_VERIFY;classification="+build.classification()+";tests="+build.discoveredTests().size());
                finished=true;
                if(!passed) throw new BuildStoppedException(build.classification().name());
                return completed;
            }
            if(task.role()==AgentRole.RELEASE_READINESS) {
                var buildArtifact=evidence.artifacts(context.revisionId()).stream().filter(a->a.type()==EngineeringArtifact.ArtifactType.BUILD_EVIDENCE).findFirst().orElseThrow();
                var build=evidence.decode(buildArtifact.content(),BuildEvidence.class);
                Set<String> requiredTests=Set.of("dev.ajaymatta.generated.CreateUrlTest#validTargetCreatesUniqueUsableCodes",
                        "dev.ajaymatta.generated.CreateUrlTest#rejectsUnsupportedSchemeMissingHostAndCredentials",
                        "dev.ajaymatta.generated.CreateUrlTest#rejectsMissingTarget",
                        "dev.ajaymatta.generated.RedirectUrlTest#createdCodeRedirectsToExactStoredTargetThroughHttp",
                        "dev.ajaymatta.generated.RedirectUrlTest#unknownCodeReturns404WithoutLocation");
                if(!new HashSet<>(build.discoveredTests()).equals(requiredTests)) throw new IllegalStateException("Required generated HTTP tests were not all executed");
                var analysis=evidence.decode(context.inputs().get("requirement").content(),RequirementAnalysis.class);
                var outputs=evidence.artifacts(context.revisionId());
                List<FileOperation> operations=outputs.stream().filter(a->a.type()==EngineeringArtifact.ArtifactType.FILE_PROPOSAL)
                        .flatMap(a->evidence.decode(a.content(),EngineeringModels.Proposal.class).operations().stream()).toList();
                var current=patches.read(workspace);
                var patchArtifact=outputs.stream().filter(a->a.type()==EngineeringArtifact.ArtifactType.MANIFEST && a.schemaVersion().equals("engineering/1.0")).findFirst().orElseThrow();
                if(!ProposalTool.manifest(current).equals(evidence.decode(patchArtifact.content(),EngineeringModels.AppliedPatch.class).afterHash())) throw new IllegalStateException("Outcome manifest drift");
                List<EngineeringModels.CriterionEvidence> trace=new ArrayList<>();
                for(var criterion:analysis.criteria()) {
                    var production=operations.stream().filter(o->o.criterionIds().contains(criterion.id()) && o.path().startsWith("src/main/java/")).map(FileOperation::path).toList();
                    var tests=operations.stream().filter(o->o.criterionIds().contains(criterion.id()) && o.path().startsWith("src/test/java/")).map(FileOperation::path).toList();
                    var executed=tests.stream().flatMap(path->build.discoveredTests().stream().filter(name->name.startsWith(path.substring("src/test/java/".length()).replace("/",".").replace(".java","")+"#"))).toList();
                    if(production.isEmpty() || tests.isEmpty() || executed.isEmpty() || !build.compiledProductionPaths().containsAll(production)
                            || build.classification()!=BuildEvidence.FailureClassification.NONE || !build.failedTests().isEmpty()) throw new IllegalStateException("Criterion lacks compiled production and executed test evidence");
                    for(var operation:operations) if(operation.type()!=FileOperation.Operation.DELETE && !Objects.equals(current.get(operation.path()),operation.content())) throw new IllegalStateException("Current artifact content drift");
                    trace.add(new EngineeringModels.CriterionEvidence(criterion.id(),production,tests,executed));
                }
                var outcome=new EngineeringModels.SliceOutcome(false,"VERIFIED_VERTICAL_SLICE_RELEASE_GATED",planHash,ProposalTool.manifest(current),build,trace,
                        outputs.stream().map(EngineeringArtifact::sha256).toList(),List.of("Stage 4 feature-completion and release-approval gate remains closed", "In-memory service; production hardening is pending", "HTTP test evidence proves this bounded runtime slice only"));
                var completed=store.finish(context,attempt,List.of(artifact(context,EngineeringArtifact.ArtifactType.ENGINEERING_OUTCOME,evidence.encode(outcome))),true,"Vertical slice verified; release readiness is false");
                finished=true;
                // This task has not met its RELEASE_APPROVED exit gate.
                store.awaitRelease(task.id());
                return completed;
            }
            var agent=agents.get(task.role()); if(agent==null) throw new IllegalArgumentException("No engineering agent for role");
            var output=agent.execute(context);
            output.artifacts().forEach(a->validator.validate(a,context));
            if(!output.proposals().isEmpty()) patches.validate(workspace,output.proposals());
            return store.finish(context,attempt,output.artifacts(),true,"Agent output validated; source proposals remain unapplied until synchronization");
        } catch(RuntimeException failure) {
            if(!finished) evidence.fail(context,attempt);
            throw failure;
        }
    }
    private EngineeringArtifact artifact(ExecutionContext context,EngineeringArtifact.ArtifactType type,String content) {
        return new EngineeringArtifact(UUID.randomUUID(),context.revisionId(),context.taskId(),type,"engineering/1.0",content,Hashes.sha256(content),context.inputHashes(),evidence.now());
    }
    public static class BuildStoppedException extends RuntimeException { public BuildStoppedException(String classification) { super(classification); } }
}
