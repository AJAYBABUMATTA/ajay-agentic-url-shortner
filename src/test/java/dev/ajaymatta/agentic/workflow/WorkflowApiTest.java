package dev.ajaymatta.agentic.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ajaymatta.agentic.execution.Hashes;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
@ActiveProfiles("test")
class WorkflowApiTest {
    private static final String URL = "/api/v1/workflows";
    private static final String VALID = """
            {"requirement":"Create a URL-shortener with HTTP 302 redirects", "repositoryPath":"greenfield"}
            """;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @Test
    void submissionPersistsPendingTaskAndAuditThenCanBeRead() throws Exception {
        String body = mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.workflow.status").value("RECEIVED"))
                .andExpect(jsonPath("$.revision.number").value(1))
                .andExpect(jsonPath("$.tasks[0].state").value("PENDING"))
                .andExpect(jsonPath("$.tasks[0].attemptCount").value(0))
                .andExpect(jsonPath("$.dependencies").isEmpty())
                .andExpect(jsonPath("$.executionEnabled").value(false))
                .andExpect(jsonPath("$.sourceMutationAllowed").value(false))
                .andExpect(jsonPath("$.audit[0].eventType").value("REQUIREMENT_RECEIVED"))
                .andReturn().getResponse().getContentAsString();
        JsonNode submitted = json.readTree(body);
        String id = submitted.at("/workflow/id").asText();
        assertThat(submitted.at("/revision/requirementHash").asText())
                .isEqualTo(Hashes.sha256(submitted.at("/revision/requirement").asText()));
        assertThat(submitted.at("/audit/0/details").asText()).doesNotContain("HTTP 302");
        mvc.perform(get(URL + "/" + id)).andExpect(status().isOk())
                .andExpect(content().json(body));
    }

    @ParameterizedTest
    @ValueSource(strings = {"status", "state", "tasks", "artifacts", "validationResults", "approvals", "output",
            "completed", "executionEnabled", "sourceMutationAllowed", "workflow", "revision"})
    void callerCannotInjectExecutionStateOrEvidence(String forbiddenField) throws Exception {
        long before = countWorkflows();
        var request = json.createObjectNode().put("requirement", "Create a shortener").put("repositoryPath", "greenfield")
                .put(forbiddenField, "Implementation completed.");
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
        assertThat(countWorkflows()).isEqualTo(before);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"requirement\":null,\"repositoryPath\":\"greenfield\"}",
            "{\"requirement\":\"   \",\"repositoryPath\":\"greenfield\"}",
            "{\"requirement\":123,\"repositoryPath\":\"greenfield\"}",
            "{\"requirement\":true,\"repositoryPath\":\"greenfield\"}",
            "{\"requirement\":\"Create\",\"repositoryPath\":{\"output\":\"complete\"}}",
            "{\"requirement\":\"Create\",\"requirement\":\"Replace\",\"repositoryPath\":\"greenfield\"}",
            "{\"requirement\":\"Create\",\"repositoryPath\":\"greenfield\"} {}",
            "not-json"})
    void malformedInputsCannotCreatePartialWorkflows(String request) throws Exception {
        long before = countWorkflows();
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        assertThat(countWorkflows()).isEqualTo(before);
    }

    @Test
    void oversizedRequirementRejected() throws Exception {
        String body = json.writeValueAsString(json.createObjectNode()
                .put("requirement", "x".repeat(10001)).put("repositoryPath", "greenfield"));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0]").exists());
    }

    @Test
    void noTaskCompletionOrWorkflowMutationEndpointExists() throws Exception {
        var submitted = json.readTree(mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andReturn().getResponse().getContentAsString());
        String id = submitted.at("/workflow/id").asText();
        String task = submitted.at("/tasks/0/id").asText();
        String completion = "{\"state\":\"SUCCEEDED\",\"output\":\"Implementation completed.\"}";
        mvc.perform(post(URL + "/" + id + "/tasks/" + task + "/complete")
                .contentType(MediaType.APPLICATION_JSON).content(completion)).andExpect(status().isNotFound());
        mvc.perform(patch(URL + "/" + id).contentType(MediaType.APPLICATION_JSON).content(completion))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(put(URL + "/" + id).contentType(MediaType.APPLICATION_JSON).content(completion))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(get(URL + "/" + id)).andExpect(jsonPath("$.tasks[0].state").value("PENDING"))
                .andExpect(jsonPath("$.workflow.status").value("RECEIVED"));
    }

    @Test
    void missingWorkflowAndInvalidIdentifierHaveProblemResponses() throws Exception {
        mvc.perform(get(URL + "/" + UUID.randomUUID())).andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
        mvc.perform(get(URL + "/not-a-uuid")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Malformed or unsupported request; only documented fields and types are accepted"));
    }

    @Test
    void healthAndOpenApiDescribeFoundationHonestly() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/prometheus")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("jvm_memory_used_bytes")));
        String spec = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var paths = json.readTree(spec).get("paths");
        assertThat(paths.size()).isEqualTo(6);
        assertThat(paths.get(URL).has("post")).isTrue();
        assertThat(paths.get(URL + "/{id}").has("get")).isTrue();
        assertThat(spec).doesNotContain("/complete");
    }

    private long countWorkflows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM workflows", Long.class);
    }
}
