package dev.ajaymatta.agentic.engineering;

import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.*;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/** HTTP-only intake/approval/inspection, real PostgreSQL and automatic executor dispatch. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties={"agentic.processing.enabled=true","agentic.processing.poll-ms=100"})
@ActiveProfiles("test") @Testcontainers @DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability
class FullGeneratedApiIT {
    @Container static PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:16-alpine");
    @TempDir static Path temp;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("agentic.operator.token",()->"test-only-operator-token");
        p.add("spring.datasource.url",postgres::getJdbcUrl); p.add("spring.datasource.username",postgres::getUsername);
        p.add("spring.datasource.password",postgres::getPassword); p.add("spring.datasource.driver-class-name",()->"org.postgresql.Driver");
        p.add("agentic.repositories.root",()->temp.resolve("sources").toString());
        p.add("agentic.workspaces.root",()->temp.resolve("workspaces").toString());
    }
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    @AfterAll static void stopWorkerBeforeDatabase(@Autowired ConfigurableApplicationContext context) {
        context.getBeansOfType(org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler.class).values().forEach(org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler::shutdown);
    }
    @Test void publicApiAutomaticallyGeneratesAndVerifiesAReviewableService() throws Exception {
        Path source=Files.createDirectories(temp.resolve("sources/greenfield")); Files.writeString(source.resolve("README.md"),"Original API fixture\n");
        var submitted=post("/api/v1/workflows",Map.of("requirement","Create URL-shortener with HTTP 302 redirects, aliases, expiresAt expiry timestamp, inspection, deactivation, total and UTC daily analytics, rate limit 60 requests per client per 60 seconds","repositoryPath","greenfield"),false);
        assertThat(submitted.statusCode()).isEqualTo(202);
        String id=json.readTree(submitted.body()).path("workflow").path("id").asText();
        String path="/api/v1/workflows/"+id;
        await().atMost(Duration.ofSeconds(30)).untilAsserted(()->assertThat(get(path).path("workflow").path("status").asText()).isEqualTo("AWAITING_CHANGE_APPROVAL"));
        var planned=get(path);
        String hash=planned.path("intelligence").path("planHash").asText();
        var approval=Map.of("expectedRevision",1,"planHash",hash,"decision","APPROVED","reason","Integration reviewer inspected exact current plan");
        assertThat(post(path+"/change-approvals",approval,false).statusCode()).isEqualTo(401);
        assertThat(post(path+"/change-approvals",approval,true).statusCode()).isEqualTo(202);
        await().atMost(Duration.ofSeconds(240)).pollInterval(Duration.ofMillis(500)).untilAsserted(()-> {
            var engineering=get(path+"/engineering");
            if(engineering.path("state").asText().equals("FAILED")) throw new IllegalStateException("Persisted failure evidence: "+engineering);
            assertThat(engineering.path("state").asText()).isEqualTo("SUCCEEDED");
        });
        var result=get(path+"/engineering"); var outcome=result.path("outcome");
        assertThat(outcome.path("releaseReady").asBoolean()).isFalse();
        assertThat(outcome.path("build").path("compiledProductionPaths").size()).isEqualTo(11);
        assertThat(outcome.path("build").path("discoveredTests").size()).isEqualTo(9);
        assertThat(outcome.path("build").path("failedTests").size()).isZero();
        assertThat(outcome.path("build").path("coverage").path("available").asBoolean()).isTrue();
        assertThat(outcome.path("traceability").size()).isEqualTo(9);
        assertThat(get(path).path("workflow").path("status").asText()).isEqualTo("AWAITING_RELEASE_APPROVAL");
        assertThat(Files.readString(source.resolve("README.md"))).isEqualTo("Original API fixture\n");
        assertThat(Files.exists(source.resolve("pom.xml"))).isFalse();
        assertThat(result.path("artifacts").toString()).contains("REDIRECT_STATUS = 302","FILE_PROPOSAL","UNIFIED_DIFF","BUILD_EVIDENCE");
        String outcomeHash="";
        for(var artifact:result.path("artifacts")) if(artifact.path("type").asText().equals("ENGINEERING_OUTCOME")) outcomeHash=artifact.path("sha256").asText();
        var release=Map.of("expectedRevision",1,"outcomeHash",outcomeHash,"decision","APPROVED","reason","Reviewed immutable outcome and all traceability");
        assertThat(post(path+"/release-approvals",release,false).statusCode()).isEqualTo(401);
        assertThat(post(path+"/release-approvals",Map.of("expectedRevision",1,"outcomeHash","0".repeat(64),"decision","APPROVED","reason","Stale evidence"),true).statusCode()).isEqualTo(409);
        assertThat(post(path+"/release-approvals",release,true).statusCode()).isEqualTo(200);
        result=get(path+"/engineering");
        assertThat(result.path("outcome").path("releaseReady").asBoolean()).isTrue();
        assertThat(result.path("outcome").path("gates").size()).isEqualTo(10);
        for(var gate:result.path("outcome").path("gates")) assertThat(gate.path("passed").asBoolean()).isTrue();
        assertThat(get(path).path("workflow").path("status").asText()).isEqualTo("RELEASE_READY");
        Path retained=Files.createDirectories(Path.of("target/stage5-full-evidence"));
        Files.writeString(retained.resolve("workflow.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(get(path)));
        Files.writeString(retained.resolve("engineering.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        Path stage4=Files.createDirectories(Path.of("target/stage5-full-evidence"));
        Files.writeString(stage4.resolve("postgres-api-release.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        var metrics=client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/actuator/prometheus")).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(metrics.statusCode()).isEqualTo(200);
        assertThat(metrics.body()).contains("agentic_release_success_rate 1.0","agentic_workflows_outcomes_total{outcome=\"release_ready\"}","agentic_agent_duration_seconds_count","agentic_workflow_duration_seconds_count");
        Files.writeString(stage4.resolve("postgres-metrics.prom"),metrics.body());
    }
    private JsonNode get(String path) throws Exception {
        var result=client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(20)).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(result.statusCode()).isEqualTo(200); return json.readTree(result.body());
    }
    private HttpResponse<String> post(String path,Object body,boolean authenticated) throws Exception {
        var request=HttpRequest.newBuilder(URI.create("http://localhost:"+port+path)).timeout(Duration.ofSeconds(20))
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        if(authenticated) request.header("X-Operator-Id","api-integration-reviewer").header("X-Operator-Token","test-only-operator-token");
        return client.send(request.build(),HttpResponse.BodyHandlers.ofString());
    }
}
