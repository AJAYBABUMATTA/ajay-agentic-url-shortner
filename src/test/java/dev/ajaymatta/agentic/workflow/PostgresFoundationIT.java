package dev.ajaymatta.agentic.workflow;

import dev.ajaymatta.agentic.workflow.api.SubmitRequirement;
import dev.ajaymatta.agentic.workflow.application.WorkflowService;
import dev.ajaymatta.agentic.intelligence.RequirementProcessor;
import dev.ajaymatta.agentic.intelligence.RevisionRequests;
import dev.ajaymatta.agentic.intelligence.RevisionService;
import dev.ajaymatta.agentic.workflow.domain.WorkflowStatus;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Opt-in real PostgreSQL verification; missing Docker fails rather than silently skipping. */
@SpringBootTest(properties="agentic.processing.enabled=false")
@AutoConfigureMockMvc
@Testcontainers
class PostgresFoundationIT {
    @TempDir static Path workspace;
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("agentic.workspaces.root", () -> workspace.resolve("workspaces").toString());
    }

    @Autowired WorkflowService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired RequirementProcessor processor;
    @Autowired RevisionService revisions;

    @Test
    void actualPostgresMigrationAndWorkflowReadback() {
        var submitted = service.submit(new SubmitRequirement("Create a URL-shortener", "greenfield"));
        assertThat(service.get(submitted.workflow().id())).isEqualTo(submitted);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM \"flyway_schema_history\" WHERE \"success\"=TRUE", Integer.class))
                .isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='public' "
                + "AND table_name IN ('workflows','workflow_revisions','agent_tasks','task_dependencies','engineering_artifacts',"
                + "'execution_attempts','validation_results','build_evidence','repository_workspaces','policy_decisions',"
                + "'approvals','recovery_decisions','rollback_actions','audit_events')", Integer.class)).isEqualTo(14);
    }

    @Test
    void actualPostgresRejectsCrossRevisionDependencies() {
        var first = service.submit(new SubmitRequirement("Create shortener", "greenfield"));
        var second = service.submit(new SubmitRequirement("Add analytics", "brownfield"));
        assertThatThrownBy(() -> jdbc.update("INSERT INTO task_dependencies VALUES (?,?,?)", first.revision().id(),
                first.tasks().getFirst().id(), second.tasks().getFirst().id())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void executionStateInjectionIsRejectedAgainstPostgres() throws Exception {
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM workflows", Integer.class);
        mvc.perform(post("/api/v1/workflows").contentType(MediaType.APPLICATION_JSON).content("""
                {"requirement":"Create shortener","repositoryPath":"greenfield","status":"RELEASE_READY"}
                """)).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM workflows", Integer.class)).isEqualTo(before);
        mvc.perform(post("/api/v1/workflows/" + UUID.randomUUID() + "/complete")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isNotFound());
    }

    @Test
    void readinessIncludesActualPostgresConnection() throws Exception {
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void actualPostgresPersistsAutomaticPlanningArtifactsAndAttempts() {
        var submitted = service.submit(new SubmitRequirement("Create URL-shortener with HTTP 302 redirects", "greenfield-url-shortener"));
        processor.process(submitted.workflow().id());
        var planned = service.get(submitted.workflow().id());
        assertThat(planned.workflow().status()).isEqualTo(WorkflowStatus.AWAITING_CHANGE_APPROVAL);
        assertThat(planned.intelligence().attempts()).hasSize(4);
        assertThat(planned.intelligence().artifacts()).hasSize(5);
        assertThat(planned.intelligence().planHash()).matches("[a-f0-9]{64}");
    }

    @Test
    void actualPostgresClarificationCreatesLineageAndInvalidatesPriorEvidence() {
        var submitted = service.submit(new SubmitRequirement("Create URL-shortener with expiry", "greenfield-url-shortener"));
        processor.process(submitted.workflow().id());
        assertThat(service.get(submitted.workflow().id()).workflow().status()).isEqualTo(WorkflowStatus.AWAITING_CLARIFICATION);
        revisions.clarify(submitted.workflow().id(), new RevisionRequests.Clarification(1, Map.of("Q-EXPIRY", "24 hours")), "integration-test-operator");
        processor.process(submitted.workflow().id());
        var clarified = service.get(submitted.workflow().id());
        assertThat(clarified.workflow().status()).isEqualTo(WorkflowStatus.AWAITING_CHANGE_APPROVAL);
        assertThat(clarified.revision().parentRevisionId()).isEqualTo(submitted.revision().id());
        assertThat(clarified.intelligence().invalidatedArtifactIds()).hasSize(2);
    }
}
