package dev.ajaymatta.agentic.planning;

import dev.ajaymatta.agentic.execution.AgentRole;
import dev.ajaymatta.agentic.intelligence.RequirementAnalysis;
import dev.ajaymatta.agentic.repository.RepositoryMap;
import dev.ajaymatta.agentic.workflow.domain.Gate;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class DynamicPlanner {
    private final GraphValidator validator;
    public DynamicPlanner(GraphValidator validator) { this.validator = validator; }

    public EngineeringPlan plan(UUID revisionId, String requirementHash, RequirementAnalysis analysis, RepositoryMap repository) {
        if (!analysis.resolved()) throw new IllegalArgumentException("Unresolved requirement cannot be planned");
        List<EngineeringPlan.PlannedTask> tasks = new ArrayList<>();
        tasks.add(task("interpret-requirement", AgentRole.REQUIREMENT_INTERPRETATION, List.of(), List.of(), List.of(), Gate.CURRENT_INPUT_HASHES, Gate.ARTIFACT_VALIDATED));
        tasks.add(task("analyze-ambiguity", AgentRole.AMBIGUITY_ANALYSIS, List.of("interpret-requirement"), List.of(), List.of(), Gate.DEPENDENCIES_SUCCEEDED, Gate.REQUIREMENT_RESOLVED));
        tasks.add(task("analyze-repository", AgentRole.REPOSITORY_ANALYSIS, List.of("analyze-ambiguity"), List.of(), List.of(), Gate.REQUIREMENT_RESOLVED, Gate.ARTIFACT_VALIDATED));
        tasks.add(task("plan-engineering", AgentRole.PLANNING, List.of("analyze-repository"), List.of(), List.of(), Gate.DEPENDENCIES_SUCCEEDED, Gate.ARTIFACT_VALIDATED));
        List<String> allCriteria = analysis.criteria().stream().map(RequirementAnalysis.Criterion::id).toList();
        List<String> allPaths = repository.components().stream().map(RepositoryMap.Component::path).toList();
        tasks.add(task("architecture", AgentRole.ARCHITECTURE, List.of("plan-engineering"), allCriteria, allPaths, Gate.DEPENDENCIES_SUCCEEDED, Gate.ARTIFACT_VALIDATED));
        tasks.add(task("security-design", AgentRole.SECURITY_RISK, List.of("plan-engineering"), allCriteria, allPaths, Gate.DEPENDENCIES_SUCCEEDED, Gate.POLICY_PASSED));
        List<String> joins = new ArrayList<>();
        Map<String, List<String>> priorImpacts = new LinkedHashMap<>();
        for (var criterion : analysis.criteria()) {
            if (!criterion.behavioral()) continue;
            String implement = "implement-" + criterion.capability();
            List<String> paths = impacts(criterion, repository);
            List<String> dependencies = new ArrayList<>(List.of("architecture", "security-design"));
            priorImpacts.forEach((key, prior) -> { if (!Collections.disjoint(prior, paths)) dependencies.add(key); });
            tasks.add(task(implement, AgentRole.IMPLEMENTATION, dependencies, List.of(criterion.id()), paths, Gate.CHANGE_APPROVED, Gate.PROPOSAL_VALID));
            String test = "test-" + criterion.capability();
            List<String> testPaths = repository.greenfield() && List.of("create","redirect").contains(criterion.capability())
                    ? List.of("src/test/java/dev/ajaymatta/generated/" + (criterion.capability().equals("create") ? "CreateUrlTest.java" : "RedirectUrlTest.java")) : paths;
            tasks.add(task(test, AgentRole.TESTING, List.of(implement), List.of(criterion.id()), testPaths, Gate.DEPENDENCIES_SUCCEEDED, Gate.PROPOSAL_VALID));
            joins.add(test); priorImpacts.put(implement, paths);
        }
        if (joins.isEmpty()) throw new IllegalArgumentException("No behavioral criteria");
        tasks.add(task("synchronize-proposals", AgentRole.PLANNING, joins, allCriteria, allPaths, Gate.DEPENDENCIES_SUCCEEDED, Gate.PATCH_APPLIED));
        tasks.add(task("validate-build", AgentRole.TESTING, List.of("synchronize-proposals"), allCriteria, allPaths, Gate.PATCH_APPLIED, Gate.BUILD_AND_TESTS_PASSED));
        tasks.add(task("documentation", AgentRole.DOCUMENTATION, List.of("validate-build"), allCriteria, allPaths, Gate.BUILD_AND_TESTS_PASSED, Gate.ARTIFACT_VALIDATED));
        tasks.add(task("security-review", AgentRole.SECURITY_RISK, List.of("validate-build"), allCriteria, allPaths, Gate.BUILD_AND_TESTS_PASSED, Gate.POLICY_PASSED));
        tasks.add(task("release-readiness", AgentRole.RELEASE_READINESS, List.of("documentation", "security-review"), allCriteria, allPaths, Gate.TRACEABILITY_COMPLETE, Gate.RELEASE_APPROVED));
        return new EngineeringPlan(revisionId, requirementHash, repository.manifestHash(), tasks, validator.validate(tasks),
                List.of(repository.greenfield() ? "Generate an original service from an empty source baseline" : "Enhance existing runtime components and preserve regression behavior",
                        "Serialize overlapping production proposals; synchronize all test branches before application",
                        "High-impact source application requires exact plan approval; release requires exact outcome approval"));
    }

    private List<String> impacts(RequirementAnalysis.Criterion criterion, RepositoryMap repository) {
        if (repository.greenfield()) {
            if (criterion.capability().equals("create")) return List.of("pom.xml", "mvnw", "mvnw.cmd", ".mvn/wrapper/maven-wrapper.properties",
                    "src/main/java/dev/ajaymatta/generated/ShortenerApplication.java", "src/main/java/dev/ajaymatta/generated/UrlStore.java", "src/main/java/dev/ajaymatta/generated/UrlController.java");
            if (criterion.capability().equals("redirect")) return List.of("src/main/java/dev/ajaymatta/generated/RedirectController.java");
            String type = criterion.capability().startsWith("analytics") ? "AnalyticsService"
                    : criterion.capability().equals("redirect") ? "RedirectController"
                    : criterion.capability().equals("create") || criterion.capability().equals("inspect") ? "UrlController"
                    : "UrlService";
            return List.of("src/main/java/dev/ajaymatta/target/" + type + ".java");
        }
        List<String> result = repository.components().stream()
                .filter(c -> List.of("controller", "service", "repository", "domain").contains(c.kind()))
                .map(RepositoryMap.Component::path).toList();
        return result.isEmpty() ? repository.components().stream().map(RepositoryMap.Component::path).toList() : result;
    }
    private EngineeringPlan.PlannedTask task(String key, AgentRole role, List<String> dependencies, List<String> criteria,
            List<String> paths, Gate entry, Gate exit) {
        return new EngineeringPlan.PlannedTask(key, role, dependencies, criteria, paths, List.of(entry), List.of(exit));
    }
}
