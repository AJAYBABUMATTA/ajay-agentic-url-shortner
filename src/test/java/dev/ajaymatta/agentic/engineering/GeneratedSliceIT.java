package dev.ajaymatta.agentic.engineering;

import dev.ajaymatta.agentic.intelligence.RequirementProcessor;
import dev.ajaymatta.agentic.intelligence.IntelligenceStore;
import dev.ajaymatta.agentic.execution.*;
import dev.ajaymatta.agentic.workflow.application.WorkflowService;
import dev.ajaymatta.agentic.workflow.api.SubmitRequirement;
import dev.ajaymatta.agentic.workflow.domain.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

/** Real nested Maven build; runs under the integration-test profile, never mocked. */
@SpringBootTest @ActiveProfiles("test")
class GeneratedSliceIT {
    @TempDir static Path temp;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->"jdbc:h2:mem:generated-slice;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        p.add("agentic.repositories.root",()->temp.resolve("sources").toString());
        p.add("agentic.workspaces.root",()->temp.resolve("workspaces").toString());
    }
    @Autowired WorkflowService workflows;
    @Autowired RequirementProcessor intelligence;
    @Autowired ChangeApprovalService approvals;
    @Autowired EngineeringProcessor engineering;
    @Autowired EngineeringStore store;
    @Autowired IntelligenceStore evidence;
    @MockitoSpyBean DeterministicEngineeringProvider provider;
    @Test void requirementGeneratesProductionAndHttpTestsThatActuallyCompileAndExecute() throws Exception {
        Files.createDirectories(temp.resolve("sources/green")); Files.writeString(temp.resolve("sources/green/README.md"),"Untouched original fixture\n");
        var submitted=workflows.submit(new SubmitRequirement("Create a URL-shortener with HTTP 302 redirects","green"));
        intelligence.process(submitted.workflow().id());
        var planned=workflows.get(submitted.workflow().id());
        approvals.decide(planned.workflow().id(),new ChangeApprovalService.Request(1,planned.intelligence().planHash(),ChangeApprovalService.Decision.APPROVED,"Reviewed exact plan"),"integration-reviewer");
        engineering.process(planned.revision().id());
        var result=store.view(planned.revision().id());
        assertThat(result.state()).withFailMessage("Persisted engineering evidence: %s",result).isEqualTo("SUCCEEDED");
        assertThat(result.outcome().releaseReady()).isFalse();
        assertThat(result.outcome().build().exitCode()).isZero();
        assertThat(result.outcome().build().compiledProductionPaths()).hasSize(4);
        assertThat(result.outcome().build().discoveredTests()).hasSize(5);
        assertThat(result.outcome().build().failedTests()).isEmpty();
        assertThat(result.outcome().build().coverage().available()).isTrue();
        assertThat(result.outcome().traceability()).hasSize(2).allMatch(t->!t.productionPaths().isEmpty() && !t.executedTests().isEmpty());
        assertThat(workflows.get(planned.workflow().id()).tasks()).filteredOn(t->t.key().equals("release-readiness")).allMatch(t->t.state()==TaskState.AWAITING_APPROVAL);
        assertThat(Files.readString(temp.resolve("sources/green/README.md"))).isEqualTo("Untouched original fixture\n");
        assertThat(Files.isRegularFile(result.workspace().repository().resolve("target/generated-shortener-1.0.0.jar"))).isTrue();
        retain("greenfield-302",result);
    }
    @Test void genuineCompilerFailurePersistsBuildEvidenceAndNeverProducesSuccessfulOutcome() throws Exception {
        failGeneratedBuild("implement-redirect",true,BuildEvidence.FailureClassification.COMPILATION);
    }
    @Test void genuineHttpTestFailurePersistsFailedCaseAndNeverProducesSuccessfulOutcome() throws Exception {
        failGeneratedBuild("test-redirect",false,BuildEvidence.FailureClassification.TEST);
    }
    private void failGeneratedBuild(String taskKey,boolean compileFailure,BuildEvidence.FailureClassification expected) throws Exception {
        // Only provider output is fault-injected; patch application and Maven process remain real.
        doAnswer(invocation -> {
            var request=invocation.getArgument(0,ModelProvider.ModelRequest.class);
            var response=(ModelProvider.ModelResponse)invocation.callRealMethod();
            var input=evidence.decode(request.context().inputs().get("task").content(),EngineeringModels.TaskInput.class);
            if(!input.task().key().equals(taskKey)) return response;
            var proposal=evidence.decode(response.content(),EngineeringModels.Proposal.class);
            var operations=proposal.operations().stream().map(operation -> new FileOperation(operation.type(),operation.path(),
                    compileFailure ? operation.content()+"\nthis is invalid Java\n" : operation.content().replace(".isEqualTo(302)",".isEqualTo(418)"),
                    operation.expectedSha256(),operation.reason(),operation.requirementId(),operation.criterionIds(),operation.taskId(),operation.inputHashes())).toList();
            return new ModelProvider.ModelResponse(response.provider(),response.model(),evidence.encode(new EngineeringModels.Proposal(operations)),response.duration());
        }).when(provider).generate(any());
        Files.createDirectories(temp.resolve("sources/green")); Files.writeString(temp.resolve("sources/green/README.md"),"Original fixture\n");
        var submitted=workflows.submit(new SubmitRequirement("Create a URL-shortener with HTTP 302 redirects","green"));
        intelligence.process(submitted.workflow().id());
        var planned=workflows.get(submitted.workflow().id());
        approvals.decide(planned.workflow().id(),new ChangeApprovalService.Request(1,planned.intelligence().planHash(),ChangeApprovalService.Decision.APPROVED,"Fault scenario reviewed"),"integration-reviewer");
        engineering.process(planned.revision().id());
        var result=store.view(planned.revision().id());
        assertThat(result.state()).isEqualTo("FAILED"); assertThat(result.outcome()).isNull();
        var artifact=result.artifacts().stream().filter(a->a.type()==EngineeringArtifact.ArtifactType.BUILD_EVIDENCE).findFirst().orElseThrow();
        var build=evidence.decode(artifact.content(),BuildEvidence.class);
        assertThat(build.exitCode()).isNotZero(); assertThat(build.classification()).isEqualTo(expected);
        if(!compileFailure) assertThat(build.failedTests()).contains("dev.ajaymatta.generated.RedirectUrlTest#createdCodeRedirectsToExactStoredTargetThroughHttp");
        assertThat(workflows.get(planned.workflow().id()).workflow().status()).isEqualTo(WorkflowStatus.SAFE_STOPPED);
        assertThat(workflows.get(planned.workflow().id()).tasks()).filteredOn(t->t.key().equals("validate-build")).allMatch(t->t.state()==TaskState.FAILED);
        assertThat(Files.readString(temp.resolve("sources/green/README.md"))).isEqualTo("Original fixture\n");
        retain(compileFailure ? "compiler-failure" : "http-test-failure",result);
    }
    private void retain(String name,EngineeringModels.View result) throws Exception {
        Path retained=Files.createDirectories(Path.of("target/stage3-api-evidence"));
        Files.writeString(retained.resolve(name+".json"),evidence.encode(result));
    }
}
