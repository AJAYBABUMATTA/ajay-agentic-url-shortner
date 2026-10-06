package dev.ajaymatta.agentic.engineering;

import dev.ajaymatta.agentic.execution.*;
import dev.ajaymatta.agentic.intelligence.*;
import dev.ajaymatta.agentic.workflow.application.WorkflowService;
import dev.ajaymatta.agentic.workflow.api.*;
import dev.ajaymatta.agentic.workflow.domain.WorkflowStatus;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class EngineeringWorkflowTest {
    @AfterEach void removeUnrecognizedFixture() throws Exception { Files.deleteIfExists(temp.resolve("sources/green/Unrecognized.java")); }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"Create URL-shortener with expiry after 24 hours","Create URL-shortener with rate limit 5 requests per client per 60 seconds"})
    void unsupportedPolicyNeverBecomesFalseFeatureReadiness(String requirement) {
        var plan=planned(requirement);
        approvals.decide(plan.workflow().id(),request(plan,ChangeApprovalService.Decision.APPROVED),"reviewer");
        engineering.process(plan.revision().id());
        assertThat(workflows.get(plan.workflow().id()).workflow().status()).isEqualTo(WorkflowStatus.SAFE_STOPPED);
        assertThat(store.view(plan.revision().id()).artifacts()).isEmpty();
        assertThat(store.view(plan.revision().id()).outcome()).isNull();
    }
    @TempDir static Path temp;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url",()->"jdbc:h2:mem:engineering-boundary;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        p.add("agentic.repositories.root",()->temp.resolve("sources").toString());
        p.add("agentic.workspaces.root",()->temp.resolve("workspaces").toString());
    }
    @Autowired WorkflowService workflows;
    @Autowired RequirementProcessor intelligence;
    @Autowired ChangeApprovalService approvals;
    @Autowired EngineeringProcessor engineering;
    @Autowired EngineeringStore store;
    @Autowired IntelligenceStore evidence;
    @Autowired MockMvc mvc;
    @BeforeEach void fixture() throws Exception {
        Files.createDirectories(temp.resolve("sources/green")); Files.writeString(temp.resolve("sources/green/README.md"),"Original fixture\n");
    }
    @Test void authenticatedExactCurrentPlanApprovalQueuesExecutionAndCannotCompleteTasks() throws Exception {
        var plan=planned("Create URL-shortener");
        String body=evidence.encode(request(plan,ChangeApprovalService.Decision.APPROVED));
        mvc.perform(post("/api/v1/workflows/"+plan.workflow().id()+"/change-approvals").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        assertThat(store.view(plan.revision().id()).state()).isEqualTo("NOT_STARTED");
        mvc.perform(post("/api/v1/workflows/"+plan.workflow().id()+"/change-approvals").header("X-Operator-Id","reviewer").header("X-Operator-Token","test-only-operator-token")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isAccepted());
        assertThat(store.view(plan.revision().id()).state()).isEqualTo("QUEUED");
        assertThat(workflows.get(plan.workflow().id()).workflow().status()).isEqualTo(WorkflowStatus.EXECUTING);
        mvc.perform(post("/api/v1/workflows/"+plan.workflow().id()+"/tasks/"+plan.tasks().getFirst().id()+"/complete").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
    }
    @Test void staleHashAndRepeatedApprovalAreRejected() {
        var plan=planned("Create URL-shortener");
        assertThatThrownBy(()->approvals.decide(plan.workflow().id(),new ChangeApprovalService.Request(1,Hashes.sha256("stale"),ChangeApprovalService.Decision.APPROVED,"reviewed"),"reviewer"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
        approvals.decide(plan.workflow().id(),request(plan,ChangeApprovalService.Decision.APPROVED),"reviewer");
        assertThatThrownBy(()->approvals.decide(plan.workflow().id(),request(plan,ChangeApprovalService.Decision.APPROVED),"reviewer"))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }
    @Test void approvalRejectionNeverQueuesOrMutatesSource() throws Exception {
        var plan=planned("Create URL-shortener");
        approvals.decide(plan.workflow().id(),request(plan,ChangeApprovalService.Decision.REJECTED),"reviewer");
        assertThat(workflows.get(plan.workflow().id()).workflow().status()).isEqualTo(WorkflowStatus.SAFE_STOPPED);
        assertThat(store.view(plan.revision().id()).state()).isEqualTo("NOT_STARTED");
        assertThat(Files.readString(temp.resolve("sources/green/README.md"))).isEqualTo("Original fixture\n");
    }
    @Test void unsupportedGenerationSafelyStopsWithoutApplyingFiles() throws Exception {
        Files.createDirectories(temp.resolve("sources/green"));
        Files.writeString(temp.resolve("sources/green/Unrecognized.java"),"class Unrecognized {}\n");
        var plan=planned("Create URL-shortener with aliases");
        approvals.decide(plan.workflow().id(),request(plan,ChangeApprovalService.Decision.APPROVED),"reviewer");
        engineering.process(plan.revision().id());
        assertThat(workflows.get(plan.workflow().id()).workflow().status()).isEqualTo(WorkflowStatus.SAFE_STOPPED);
        assertThat(store.view(plan.revision().id()).artifacts()).isEmpty();
        assertThat(store.view(plan.revision().id()).outcome()).isNull();
    }
    @Test void agentProposalIncludesExactTaskCriteriaAndApprovedPaths() {
        var plan=planned("Create URL-shortener");
        var task=plan.intelligence().plan().tasks().stream().filter(t->t.key().equals("implement-create")).findFirst().orElseThrow();
        var id=plan.tasks().stream().filter(t->t.key().equals(task.key())).findFirst().orElseThrow().id();
        var req=evidence.artifact(plan.revision().id(),EngineeringArtifact.ArtifactType.REQUIREMENT_ANALYSIS).orElseThrow();
        String content=evidence.encode(new EngineeringModels.TaskInput(task,Map.of("README.md","Original fixture\n")));
        var input=new EngineeringArtifact(UUID.randomUUID(),plan.revision().id(),id,EngineeringArtifact.ArtifactType.MANIFEST,"engineering-input/1.0",content,Hashes.sha256(content),List.of(req.sha256()),evidence.now());
        var context=new ExecutionContext(plan.workflow().id(),plan.revision().id(),id,plan.revision().requirement(),Map.of("requirement",req,"task",input),List.of(req.sha256(),input.sha256()));
        var provider=new DeterministicEngineeringProvider(evidence,new TrustedBuildAssets("."));
        var agent=new EngineeringAgent(AgentRole.IMPLEMENTATION,provider,evidence);
        var output=agent.execute(context);
        assertThat(output.proposals()).extracting(FileOperation::path).containsExactlyInAnyOrderElementsOf(task.impactedPaths());
        assertThat(output.proposals()).allMatch(o->o.taskId().equals(id) && o.criterionIds().equals(List.of("AC-CREATE")) && o.inputHashes().equals(context.inputHashes()));
        new EngineeringValidator(evidence).validate(output.artifacts().getFirst(),context);
    }
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"cancel","safe-stop","rollback","release-approvals"})
    void governanceActionsRequireAuthenticatedOperatorAndCurrentRevision(String action) throws Exception {
        var plan=planned("Create URL-shortener");
        String body=action.equals("release-approvals") ? evidence.encode(new ReleaseApprovalService.Request(2,Hashes.sha256("outcome"),ChangeApprovalService.Decision.APPROVED,"Reviewed"))
                : evidence.encode(new ExecutionControlService.Request(2,"Reviewed stop"));
        String uri="/api/v1/workflows/"+plan.workflow().id()+"/"+action;
        mvc.perform(post(uri).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post(uri).header("X-Operator-Id","reviewer").header("X-Operator-Token","test-only-operator-token").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
        assertThat(workflows.get(plan.workflow().id()).workflow().status()).isEqualTo(WorkflowStatus.AWAITING_CHANGE_APPROVAL);
    }
    private WorkflowDetails planned(String requirement) {
        var result=workflows.submit(new SubmitRequirement(requirement,"green")); intelligence.process(result.workflow().id()); return workflows.get(result.workflow().id());
    }
    private ChangeApprovalService.Request request(WorkflowDetails workflow,ChangeApprovalService.Decision decision) { return new ChangeApprovalService.Request(1,workflow.intelligence().planHash(),decision,"Reviewed exact plan"); }
}
