package dev.ajaymatta.agentic.engineering;

import dev.ajaymatta.agentic.execution.*;
import dev.ajaymatta.agentic.intelligence.*;
import dev.ajaymatta.agentic.workflow.application.WorkflowService;
import dev.ajaymatta.agentic.workflow.api.*;
import dev.ajaymatta.agentic.workflow.domain.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.time.Duration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest @ActiveProfiles("test")
class RecoveryGovernanceIT {
    @TempDir static Path temp;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->"jdbc:h2:mem:stage4;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        p.add("agentic.repositories.root",()->temp.resolve("sources").toString());
        p.add("agentic.workspaces.root",()->temp.resolve("workspaces").toString());
    }
    @Autowired WorkflowService workflows;
    @Autowired RequirementProcessor intelligence;
    @Autowired ChangeApprovalService approvals;
    @Autowired ReleaseApprovalService releases;
    @Autowired RevisionService revisions;
    @Autowired EngineeringProcessor engineering;
    @Autowired ExecutionControlService control;
    @Autowired EngineeringStore store;
    @Autowired ProposalTool files;
    @Autowired IntelligenceStore evidence;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean DeterministicEngineeringProvider provider;
    @Test void brownfieldPreservesRuntimeAndRegressionThenExactApprovalAndRollback() throws Exception {
        var plan=brown("brown",false);
        CountDownLatch parallel=new CountDownLatch(2);
        doAnswer(call->{
            var request=call.getArgument(0,ModelProvider.ModelRequest.class);
            var task=evidence.decode(request.context().inputs().get("task").content(),EngineeringModels.TaskInput.class).task();
            if(Set.of("architecture","security-design").contains(task.key())) {
                parallel.countDown(); assertThat(parallel.await(10,TimeUnit.SECONDS)).as("Ready branches execute concurrently").isTrue();
            }
            return call.callRealMethod();
        }).when(provider).generate(any());
        engineering.process(plan.revision().id());
        var result=successful(plan,"brownfield");
        assertThat(result.outcome().build().discoveredTests()).hasSize(7).contains("dev.ajaymatta.target.UrlServiceTest#createdTargetCanBeResolvedAndMissingCodeReturnsNull");
        assertThat(result.outcome().build().compiledProductionPaths()).hasSize(3);
        assertThat(files.read(result.workspace()).get(BrownfieldSources.ROOT+"UrlController.java")).contains("service.recordRedirect(code)","service.dailyClicks(code)");
        assertThatThrownBy(()->releases.decide(plan.workflow().id(),release(result,Hashes.sha256("stale"),ChangeApprovalService.Decision.APPROVED),"reviewer")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        String candidate=outcomeHash(result);
        assertThat(releases.decide(plan.workflow().id(),release(result,candidate,ChangeApprovalService.Decision.APPROVED),"reviewer")).isTrue();
        var released=store.view(plan.revision().id());
        assertThat(released.outcome().releaseReady()).isTrue(); assertThat(released.outcome().gates()).hasSize(10).allMatch(EngineeringModels.GateCheck::passed);
        assertThat(released.approvals()).anyMatch(a->a.evidenceHash().equals(candidate));
        assertThatThrownBy(()->releases.decide(plan.workflow().id(),release(result,candidate,ChangeApprovalService.Decision.APPROVED),"reviewer")).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        control.rollback(plan.workflow().id(),new ExecutionControlService.Request(1,"Restore approved baseline"),"reviewer");
        assertThat(workflows.get(plan.workflow().id()).workflow().status()).isEqualTo(WorkflowStatus.ROLLED_BACK);
        var restored=store.view(plan.revision().id()); assertThat(restored.outcome()).isNull();
        assertThat(restored.rollbacks()).hasSize(1).allMatch(RollbackAction::verified);
        assertThat(ProposalTool.manifest(files.read(restored.workspace()))).isEqualTo(restored.workspace().baselineManifestHash());
        retain("rollback",restored);
    }
    @Test void genuineCompilerFailureIsDiagnosedAndProductionRepairedWithoutWeakeningTests() throws Exception {
        var plan=brown("repair",true); engineering.process(plan.revision().id()); var result=successful(plan,"compiler-repair");
        var builds=builds(result); assertThat(builds).hasSize(2);
        assertThat(builds.getFirst().classification()).isEqualTo(BuildEvidence.FailureClassification.COMPILATION);
        assertThat(builds.getFirst().stdout()).contains("MissingApplication"); assertThat(builds.getLast().exitCode()).isZero();
        assertThat(result.recovery()).hasSize(1).allMatch(r->r.action().equals("REPAIR") && r.repairArtifactId()!=null && r.maximumAttempts()==3);
        assertThat(result.artifacts()).anyMatch(a->a.type()==EngineeringArtifact.ArtifactType.DIAGNOSIS).anyMatch(a->a.type()==EngineeringArtifact.ArtifactType.TASK_PLAN);
        assertThat(files.read(result.workspace()).get(BrownfieldSources.ROOT+"TargetApplication.java")).contains("TargetApplication.class").doesNotContain("MissingApplication.class");
        assertThat(Files.readString(temp.resolve("sources/repair/"+BrownfieldSources.ROOT+"TargetApplication.java"))).contains("MissingApplication.class");
        assertThat(workflows.get(plan.workflow().id()).tasks()).filteredOn(t->t.key().equals("validate-build")).allMatch(t->t.attemptCount()==2);
    }
    @Test void genuineRuntimeHttpFailureRepairsProductionAndExecutesUnchangedTests() throws Exception {
        doAnswer(call->{
            var request=call.getArgument(0,ModelProvider.ModelRequest.class); var response=(ModelProvider.ModelResponse)call.callRealMethod();
            var task=evidence.decode(request.context().inputs().get("task").content(),EngineeringModels.TaskInput.class).task();
            if(!task.key().equals("implement-redirect")) return response;
            var proposal=evidence.decode(response.content(),EngineeringModels.Proposal.class);
            var operations=proposal.operations().stream().map(o->new FileOperation(o.type(),o.path(),o.content().replace("ResponseEntity.status(302)","ResponseEntity.status(418)"),o.expectedSha256(),o.reason(),o.requirementId(),o.criterionIds(),o.taskId(),o.inputHashes())).toList();
            return new ModelProvider.ModelResponse(response.provider(),response.model(),evidence.encode(new EngineeringModels.Proposal(operations)),response.duration());
        }).when(provider).generate(any());
        var plan=green("http-repair"); engineering.process(plan.revision().id()); var result=successful(plan,"http-repair");
        assertThat(builds(result)).hasSize(2); assertThat(builds(result).getFirst().failedTests()).contains("dev.ajaymatta.generated.RedirectUrlTest#createdCodeRedirectsToExactStoredTargetThroughHttp");
        assertThat(files.read(result.workspace()).get(GeneratedServiceSources.TEST+"RedirectUrlTest.java")).isEqualTo(GeneratedServiceSources.redirectTest(302));
        assertThat(result.recovery()).hasSize(1); assertThat(result.outcome().build().discoveredTests()).hasSize(5);
    }
    @Test void unsupportedCompilerFailureRestoresBaselineAndSafelyStops() throws Exception {
        var plan=brown("unsupported",true);
        engineering.process(plan.revision().id()); var result=store.view(plan.revision().id());
        assertThat(workflows.get(plan.workflow().id()).workflow().status()).isEqualTo(WorkflowStatus.SAFE_STOPPED);
        assertThat(result.outcome()).isNull(); assertThat(result.rollbacks()).hasSize(1).allMatch(RollbackAction::verified);
        assertThat(ProposalTool.manifest(files.read(result.workspace()))).isEqualTo(result.workspace().baselineManifestHash());
        assertThat(builds(result)).hasSize(1).allMatch(b->b.classification()==BuildEvidence.FailureClassification.COMPILATION);
        assertThat(result.recovery()).hasSize(1).allMatch(r->r.action().equals("RESTORE_BASELINE")); retain("unsupported-safe-stop",result);
    }
    @Test void boundedRepairAttemptsStopAfterThreeRealFailures() throws Exception {
        var plan=brown("bounded",true);
        doAnswer(call->{
            var request=call.getArgument(0,ModelProvider.ModelRequest.class); var response=(ModelProvider.ModelResponse)call.callRealMethod();
            if(request.role()!=AgentRole.REPAIR) return response;
            var proposal=evidence.decode(response.content(),EngineeringModels.Proposal.class);
            var ops=proposal.operations().stream().map(o->new FileOperation(o.type(),o.path(),o.content().replace("TargetApplication.class","MissingApplication.class")+"\n// unsuccessful scoped repair\n",o.expectedSha256(),o.reason(),o.requirementId(),o.criterionIds(),o.taskId(),o.inputHashes())).toList();
            return new ModelProvider.ModelResponse(response.provider(),response.model(),evidence.encode(new EngineeringModels.Proposal(ops)),response.duration());
        }).when(provider).generate(any());
        engineering.process(plan.revision().id()); var result=store.view(plan.revision().id());
        assertThat(builds(result)).hasSize(3).allMatch(b->b.classification()==BuildEvidence.FailureClassification.COMPILATION);
        assertThat(result.recovery()).hasSize(3).anyMatch(r->r.action().equals("HUMAN_INTERVENTION"));
        assertThat(result.outcome()).isNull(); assertThat(result.rollbacks()).allMatch(RollbackAction::verified); retain("bounded-safe-stop",result);
    }
    @Test void rejectingOutcomeRestoresBaselineAndKeepsRejectionEvidence() throws Exception {
        var plan=green("rejected"); engineering.process(plan.revision().id()); var result=successful(plan,"before-rejection");
        releases.decide(plan.workflow().id(),release(result,outcomeHash(result),ChangeApprovalService.Decision.REJECTED),"reviewer");
        var stopped=store.view(plan.revision().id()); assertThat(stopped.outcome()).isNull();
        assertThat(stopped.approvals()).anyMatch(a->a.kind()==dev.ajaymatta.agentic.governance.Approval.Kind.RELEASE && a.decision()==dev.ajaymatta.agentic.governance.Approval.Decision.REJECTED);
        assertThat(stopped.rollbacks()).hasSize(1).allMatch(RollbackAction::verified);
    }
    @Test void upstreamChangeInvalidatesReleaseAndReplanningCreatesFreshLineage() throws Exception {
        var plan=green("upstream"); engineering.process(plan.revision().id()); var result=successful(plan,"before-upstream-change");
        Files.writeString(temp.resolve("sources/upstream/README.md"),"Upstream changed\n");
        assertThat(releases.decide(plan.workflow().id(),release(result,outcomeHash(result),ChangeApprovalService.Decision.APPROVED),"reviewer")).isFalse();
        assertThat(store.view(plan.revision().id()).outcome()).isNull();
        revisions.replan(plan.workflow().id(),new RevisionRequests.Replan(1,"Repository changed","Create URL-shortener with HTTP 301 redirects"),"reviewer");
        intelligence.process(plan.workflow().id()); var revised=workflows.get(plan.workflow().id());
        assertThat(revised.revision().parentRevisionId()).isEqualTo(plan.revision().id());
        assertThat(revised.intelligence().planHash()).isNotEqualTo(plan.intelligence().planHash());
        assertThat(revised.intelligence().reusedArtifactIds()).isEmpty(); assertThat(revised.intelligence().invalidatedArtifactIds()).isNotEmpty();
        assertThat(store.approved(revised.revision().id(),revised.intelligence().planHash())).isFalse();
    }
    @Test void cancellationDuringRealBuildKillsProcessAndRestoresBaseline() throws Exception {
        var plan=green("cancelled");
        try(var pool=Executors.newSingleThreadExecutor()) {
            var future=pool.submit(()->engineering.process(plan.revision().id()));
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(30)).until(()->workflows.get(plan.workflow().id()).tasks().stream().anyMatch(t->t.key().equals("validate-build") && t.state()==TaskState.RUNNING));
            control.request(plan.workflow().id(),new ExecutionControlService.Request(1,"Cancel running verification"),"reviewer",true);
            future.get(30,TimeUnit.SECONDS);
        }
        var result=store.view(plan.revision().id()); assertThat(workflows.get(plan.workflow().id()).workflow().status()).isEqualTo(WorkflowStatus.CANCELLED);
        assertThat(result.outcome()).isNull(); assertThat(result.rollbacks()).hasSize(1).allMatch(RollbackAction::verified);
        assertThat(builds(result)).hasSize(1); assertThat(builds(result).getFirst().exitCode()).isNotZero(); retain("cancellation",result);
    }
    @Test void ambiguousRequirementCannotTouchSourceUntilAuthenticatedClarificationRevision() throws Exception {
        Files.createDirectories(temp.resolve("sources/ambiguous")); Files.writeString(temp.resolve("sources/ambiguous/README.md"),"Original fixture\n");
        var submitted=workflows.submit(new SubmitRequirement("Create URL-shortener with HTTP 301 and HTTP 302 redirects","ambiguous"));
        intelligence.process(submitted.workflow().id()); var paused=workflows.get(submitted.workflow().id());
        assertThat(paused.workflow().status()).isEqualTo(WorkflowStatus.AWAITING_CLARIFICATION);
        assertThat(store.view(paused.revision().id()).workspace()).isNull();
        revisions.clarify(paused.workflow().id(),new RevisionRequests.Clarification(1,Map.of("Q-REDIRECT","301")),"reviewer");
        intelligence.process(paused.workflow().id()); var plan=workflows.get(paused.workflow().id());
        assertThat(plan.workflow().currentRevision()).isEqualTo(2); assertThat(plan.revision().parentRevisionId()).isEqualTo(paused.revision().id());
        approvals.decide(plan.workflow().id(),new ChangeApprovalService.Request(2,plan.intelligence().planHash(),ChangeApprovalService.Decision.APPROVED,"Reviewed clarified plan"),"reviewer");
        engineering.process(plan.revision().id()); var result=successful(plan,"ambiguous-clarified");
        assertThat(files.read(result.workspace()).get(GeneratedServiceSources.ROOT+"RedirectController.java")).contains("ResponseEntity.status(301)");
    }
    private WorkflowDetails green(String name) throws Exception {
        Files.createDirectories(temp.resolve("sources/"+name)); Files.writeString(temp.resolve("sources/"+name+"/README.md"),"Original fixture\n");
        return approved("Create URL-shortener with HTTP 302 redirects",name);
    }
    private WorkflowDetails brown(String name,boolean broken) throws Exception {
        Path source=Path.of("scenario-repositories/url-shortener");
        try(var paths=Files.walk(source)) {
            for(Path file:paths.filter(Files::isRegularFile).toList()) {
                Path target=temp.resolve("sources/"+name).resolve(source.relativize(file)); Files.createDirectories(target.getParent());
                Files.writeString(target,Files.readString(file));
            }
        }
        if(broken) {
            Path boot=temp.resolve("sources/"+name+"/"+BrownfieldSources.ROOT+"TargetApplication.java");
            Files.writeString(boot,Files.readString(boot).replace("TargetApplication.class",name.equals("unsupported") ? "UnknownApplication.class" : "MissingApplication.class"));
        }
        return approved("Add total and UTC daily click analytics to URL-shortener",name);
    }
    private WorkflowDetails approved(String requirement,String name) {
        var submitted=workflows.submit(new SubmitRequirement(requirement,name)); intelligence.process(submitted.workflow().id());
        var planned=workflows.get(submitted.workflow().id()); assertThat(planned.workflow().status()).isEqualTo(WorkflowStatus.AWAITING_CHANGE_APPROVAL);
        approvals.decide(planned.workflow().id(),new ChangeApprovalService.Request(1,planned.intelligence().planHash(),ChangeApprovalService.Decision.APPROVED,"Reviewed scope including bounded recovery"),"reviewer"); return planned;
    }
    private EngineeringModels.View successful(WorkflowDetails plan,String name) throws Exception {
        var result=store.view(plan.revision().id()); retain(name,result);
        assertThat(result.state()).withFailMessage("Evidence: %s",evidence.encode(result)).isEqualTo("SUCCEEDED");
        assertThat(result.outcome().featureComplete()).isTrue(); assertThat(result.outcome().releaseReady()).isFalse(); return result;
    }
    private List<BuildEvidence> builds(EngineeringModels.View result) { return result.artifacts().stream().filter(a->a.type()==EngineeringArtifact.ArtifactType.BUILD_EVIDENCE).map(a->evidence.decode(a.content(),BuildEvidence.class)).toList(); }
    private String outcomeHash(EngineeringModels.View result) { return result.artifacts().stream().filter(a->a.type()==EngineeringArtifact.ArtifactType.ENGINEERING_OUTCOME).reduce((a,b)->b).orElseThrow().sha256(); }
    private ReleaseApprovalService.Request release(EngineeringModels.View result,String hash,ChangeApprovalService.Decision decision) { return new ReleaseApprovalService.Request(1,hash,decision,"Reviewed exact outcome"); }
    private void retain(String name,EngineeringModels.View result) throws Exception { Files.createDirectories(Path.of("target/stage4-evidence")); Files.writeString(Path.of("target/stage4-evidence/"+name+".json"),evidence.encode(result)); }
}
