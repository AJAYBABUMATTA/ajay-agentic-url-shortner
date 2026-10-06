package dev.ajaymatta.agentic.intelligence;

import dev.ajaymatta.agentic.workflow.api.*;
import dev.ajaymatta.agentic.workflow.application.WorkflowService;
import dev.ajaymatta.agentic.workflow.domain.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IntelligenceWorkflowTest {
    @TempDir static Path temp;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        properties.add("agentic.operator.token", () -> "test-only-operator-token");
        properties.add("spring.datasource.url", () -> "jdbc:h2:mem:intelligence;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        properties.add("agentic.repositories.root", () -> temp.resolve("sources").toString());
        properties.add("agentic.workspaces.root", () -> temp.resolve("workspaces").toString());
    }
    @Autowired WorkflowService service;
    @Autowired RequirementProcessor processor;
    @Autowired RevisionService revisions;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @BeforeEach void fixtures() throws Exception {
        Files.createDirectories(temp.resolve("sources/greenfield"));
        Files.writeString(temp.resolve("sources/greenfield/README.md"),"Original empty-source fixture");
        Files.createDirectories(temp.resolve("sources/brownfield/src/main/java/app"));
        Files.writeString(temp.resolve("sources/brownfield/src/main/java/app/UrlService.java"),"@Service class UrlService {}\n");
        Files.writeString(temp.resolve("sources/brownfield/src/main/java/app/UrlController.java"),"@RestController class UrlController { UrlService service; @GetMapping(\"/{code}\") void redirect() {} }\n");
    }

    @Test void clearIntakeAutomaticallyDispatchesAnalysisAndPersistsARequirementSpecificGraph() {
        var submitted = submit("Create a URL-shortener with HTTP 302 redirects","greenfield");
        processor.process(submitted.workflow().id());
        var planned = service.get(submitted.workflow().id());
        assertThat(planned.workflow().status()).isEqualTo(WorkflowStatus.AWAITING_CHANGE_APPROVAL);
        assertThat(planned.intelligence().planHash()).matches("[a-f0-9]{64}");
        assertThat(planned.intelligence().attempts()).hasSize(4).allMatch(a -> a.state().name().equals("SUCCEEDED"));
        assertThat(planned.tasks()).filteredOn(t -> t.role().name().equals("IMPLEMENTATION")).allMatch(t -> t.state() == TaskState.PENDING);
        assertThat(planned.sourceMutationAllowed()).isFalse();
        assertThat(planned.intelligence().repository().greenfield()).isTrue();
        assertThat(planned.intelligence().artifacts()).hasSize(5);
    }

    @Test void ambiguousRequirementNeverCreatesWorkspaceAndIncompleteClarificationDoesNotUnblockIt() throws Exception {
        var submitted = submit("Create URL-shortener with expiry","greenfield");
        processor.process(submitted.workflow().id());
        var paused = service.get(submitted.workflow().id());
        assertThat(paused.workflow().status()).isEqualTo(WorkflowStatus.AWAITING_CLARIFICATION);
        assertThat(Files.exists(temp.resolve("workspaces").resolve(submitted.workflow().id().toString()))).isFalse();
        var next = revisions.clarify(submitted.workflow().id(),new RevisionRequests.Clarification(1,Map.of("Q-EXPIRY","soon")),"test-operator");
        processor.process(next.workflow().id());
        assertThat(service.get(next.workflow().id()).workflow().status()).isEqualTo(WorkflowStatus.AWAITING_CLARIFICATION);
        assertThat(Files.exists(temp.resolve("workspaces").resolve(submitted.workflow().id().toString()))).isFalse();
    }

    @Test void authenticatedClarificationCreatesChildRevisionAndInvalidatesDerivedEvidence() throws Exception {
        var submitted = submit("Create URL-shortener with expiry","greenfield"); processor.process(submitted.workflow().id());
        String endpoint = "/api/v1/workflows/"+submitted.workflow().id()+"/clarifications";
        String body = "{\"expectedRevision\":1,\"answers\":{\"Q-EXPIRY\":\"24 hours\"}}";
        mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        assertThat(service.get(submitted.workflow().id()).workflow().currentRevision()).isEqualTo(1);
        mvc.perform(post(endpoint).header("X-Operator-Id","test-operator").header("X-Operator-Token","test-only-operator-token")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isAccepted());
        processor.process(submitted.workflow().id());
        var current = service.get(submitted.workflow().id());
        assertThat(current.revision().parentRevisionId()).isEqualTo(submitted.revision().id());
        assertThat(current.workflow().currentRevision()).isEqualTo(2);
        assertThat(current.workflow().status()).isEqualTo(WorkflowStatus.AWAITING_CHANGE_APPROVAL);
        assertThat(current.intelligence().invalidatedArtifactIds()).hasSize(2);
        assertThat(current.intelligence().requirement().criteria()).anyMatch(c -> c.capability().equals("expiry") && c.description().contains("24 hours"));
        mvc.perform(post(endpoint).header("X-Operator-Id","test-operator").header("X-Operator-Token","test-only-operator-token")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict());
    }

    @Test void replanningReusesOnlyAnUnchangedVerifiedInventoryAndInvalidatesApproval() throws Exception {
        var submitted = submit("Create URL-shortener","brownfield"); processor.process(submitted.workflow().id());
        var original = service.get(submitted.workflow().id());
        var planArtifact = original.intelligence().artifacts().stream().filter(a -> a.type().name().equals("TASK_PLAN")).findFirst().orElseThrow();
        jdbc.update("INSERT INTO approvals(id,revision_id,artifact_id,kind,evidence_hash,actor_id,decision,reason,created_at) VALUES (?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                UUID.randomUUID(),original.revision().id(),planArtifact.id(),"CHANGE",planArtifact.sha256(),"test-operator","APPROVED","reviewed");
        revisions.replan(submitted.workflow().id(),new RevisionRequests.Replan(1,"Add requested analytics","Add total and UTC daily analytics to URL-shortener"),"test-operator");
        processor.process(submitted.workflow().id());
        var next = service.get(submitted.workflow().id());
        assertThat(next.intelligence().reusedArtifactIds()).hasSize(1);
        assertThat(next.intelligence().planHash()).isNotEqualTo(original.intelligence().planHash());
        assertThat(next.intelligence().plan().tasks()).anyMatch(t -> t.key().equals("implement-analytics-daily"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approvals WHERE revision_id=? AND invalidated_at IS NOT NULL",Integer.class,original.revision().id())).isEqualTo(1);
        Files.writeString(temp.resolve("sources/brownfield/src/main/java/app/UrlService.java"),"@Service class UrlService { long clicks; }\n");
        revisions.replan(submitted.workflow().id(),new RevisionRequests.Replan(2,"Upstream code changed",null),"test-operator");
        processor.process(submitted.workflow().id());
        var changed = service.get(submitted.workflow().id());
        assertThat(changed.intelligence().reusedArtifactIds()).isEmpty();
        assertThat(changed.intelligence().repository().manifestHash()).isNotEqualTo(next.intelligence().repository().manifestHash());
    }

    @Test void repositoryPolicyFailureStopsWithoutProductionChanges() {
        var submitted = submit("Create URL-shortener","missing-repository"); processor.process(submitted.workflow().id());
        assertThat(service.get(submitted.workflow().id()).workflow().status()).isEqualTo(WorkflowStatus.SAFE_STOPPED);
        assertThat(service.get(submitted.workflow().id()).sourceMutationAllowed()).isFalse();
    }

    @Test void unknownClarificationQuestionsAndCallerSuppliedResultsAreRejected() throws Exception {
        var submitted = submit("Create URL-shortener with expiry","greenfield"); processor.process(submitted.workflow().id());
        var endpoint = "/api/v1/workflows/"+submitted.workflow().id()+"/clarifications";
        mvc.perform(post(endpoint).header("X-Operator-Id","test-operator").header("X-Operator-Token","test-only-operator-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":1,\"answers\":{\"other\":\"24 hours\"}}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(endpoint).header("X-Operator-Id","test-operator").header("X-Operator-Token","test-only-operator-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":1,\"answers\":{\"Q-EXPIRY\":\"24 hours\"},\"status\":\"SUCCEEDED\"}"))
                .andExpect(status().isBadRequest());
        assertThat(service.get(submitted.workflow().id()).workflow().currentRevision()).isEqualTo(1);
    }

    private WorkflowDetails submit(String requirement,String repo) { return service.submit(new SubmitRequirement(requirement,repo)); }
}
