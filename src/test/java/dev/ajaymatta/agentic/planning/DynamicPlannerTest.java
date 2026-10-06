package dev.ajaymatta.agentic.planning;

import dev.ajaymatta.agentic.execution.*;
import dev.ajaymatta.agentic.intelligence.RequirementInterpreter;
import dev.ajaymatta.agentic.repository.RepositoryAnalyzer;
import dev.ajaymatta.agentic.workflow.domain.Gate;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class DynamicPlannerTest {
    private final GraphValidator graphs = new GraphValidator();
    private final DynamicPlanner planner = new DynamicPlanner(graphs);
    private final RequirementInterpreter interpreter = new RequirementInterpreter();
    private final RepositoryAnalyzer analyzer = new RepositoryAnalyzer();

    @Test void requirementsChangeGraphAndRepositoriesChangeImpactsAndSequencing() {
        var green = analyzer.analyze(Hashes.sha256("empty"),Map.of());
        var brown = analyzer.analyze(Hashes.sha256("brown"), Map.of("src/main/java/app/UrlService.java", "@Service class UrlService {}",
                "src/main/java/app/UrlController.java", "@RestController class UrlController { UrlService service; @GetMapping(\"/{code}\") void redirect() {} }"));
        var base = interpreter.interpret("Create URL-shortener",Map.of());
        var analytics = interpreter.interpret("Add total and UTC daily analytics to URL-shortener",Map.of());
        var a = planner.plan(UUID.randomUUID(),Hashes.sha256("base"),base,green);
        var b = planner.plan(UUID.randomUUID(),Hashes.sha256("analytics"),analytics,green);
        var c = planner.plan(UUID.randomUUID(),Hashes.sha256("analytics"),analytics,brown);
        assertThat(a.tasks()).extracting(EngineeringPlan.PlannedTask::key).contains("implement-create").doesNotContain("implement-analytics-total");
        assertThat(b.tasks()).extracting(EngineeringPlan.PlannedTask::key).contains("implement-analytics-total","implement-analytics-daily").doesNotContain("implement-create");
        assertThat(c.tasks().stream().filter(t -> t.key().equals("implement-analytics-daily")).findFirst().orElseThrow().dependencies()).contains("implement-analytics-total");
        assertThat(c.tasks().stream().filter(t -> t.key().equals("implement-analytics-total")).findFirst().orElseThrow().impactedPaths()).contains("src/main/java/app/UrlService.java");
        assertThat(brown.dataFlows()).anyMatch(f -> f.fromPath().endsWith("UrlController.java") && f.toPath().endsWith("UrlService.java"));
        assertThat(a.executionLayers().values()).anyMatch(layer -> layer.contains("architecture") && layer.contains("security-design"));
        assertThat(a.tasks().stream().filter(t -> t.key().equals("synchronize-proposals")).findFirst().orElseThrow().dependencies()).containsExactly("test-create","test-redirect");
    }
    @Test void unclarifiedRequirementCannotProducePlan() {
        assertThatThrownBy(() -> planner.plan(UUID.randomUUID(),Hashes.sha256("vague"),interpreter.interpret("Make it better",Map.of()),analyzer.analyze(Hashes.sha256("empty"),Map.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void invalidGraphsRejectCyclesMissingAndDuplicateDependencies() {
        assertThatThrownBy(() -> graphs.validate(List.of(node("a",List.of("b")),node("b",List.of("a"))))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> graphs.validate(List.of(node("a",List.of("missing"))))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> graphs.validate(List.of(node("a",List.of()),node("a",List.of())))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> graphs.validate(List.of(node("a",List.of()),node("b",List.of("a","a"))))).isInstanceOf(IllegalArgumentException.class);
    }
    private EngineeringPlan.PlannedTask node(String key,List<String> deps) { return new EngineeringPlan.PlannedTask(key,AgentRole.PLANNING,deps,List.of(),List.of(),List.of(Gate.DEPENDENCIES_SUCCEEDED),List.of(Gate.ARTIFACT_VALIDATED)); }
}
