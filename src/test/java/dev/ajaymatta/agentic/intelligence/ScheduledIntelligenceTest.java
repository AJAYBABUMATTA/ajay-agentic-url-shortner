package dev.ajaymatta.agentic.intelligence;

import dev.ajaymatta.agentic.workflow.api.SubmitRequirement;
import dev.ajaymatta.agentic.workflow.application.WorkflowService;
import dev.ajaymatta.agentic.workflow.domain.WorkflowStatus;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties={"agentic.processing.enabled=true","agentic.processing.poll-ms=100"})
@ActiveProfiles("test")
class ScheduledIntelligenceTest {
    @TempDir static Path temp;
    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> "jdbc:h2:mem:scheduled;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        properties.add("agentic.workspaces.root", () -> temp.resolve("workspaces").toString());
    }
    @Autowired WorkflowService service;
    @Test void submittedRequirementReachesPlanWithoutAUserStageCommand() {
        var submitted = service.submit(new SubmitRequirement("Create a URL-shortener with HTTP 302 redirects","greenfield-url-shortener"));
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var current = service.get(submitted.workflow().id());
            assertThat(current.workflow().status()).isEqualTo(WorkflowStatus.AWAITING_CHANGE_APPROVAL);
            assertThat(current.intelligence().attempts()).hasSize(4);
            assertThat(current.sourceMutationAllowed()).isFalse();
        });
    }
}
