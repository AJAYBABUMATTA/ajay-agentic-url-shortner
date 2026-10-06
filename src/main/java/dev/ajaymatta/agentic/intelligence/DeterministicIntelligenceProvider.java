package dev.ajaymatta.agentic.intelligence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.ajaymatta.agentic.execution.*;
import dev.ajaymatta.agentic.planning.DynamicPlanner;
import dev.ajaymatta.agentic.repository.RepositoryAnalyzer;
import dev.ajaymatta.agentic.repository.RepositoryMap;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DeterministicIntelligenceProvider implements ModelProvider {
    private final RequirementInterpreter interpreter;
    private final RepositoryAnalyzer analyzer;
    private final DynamicPlanner planner;
    private final ObjectMapper json;
    public DeterministicIntelligenceProvider(RequirementInterpreter interpreter, RepositoryAnalyzer analyzer,
            DynamicPlanner planner, ObjectMapper json) {
        this.interpreter = interpreter; this.analyzer = analyzer; this.planner = planner; this.json = json;
    }
    @Override public ModelResponse generate(ModelRequest request) {
        Instant start = Instant.now();
        var context = request.context();
        try {
            Object output = switch (request.role()) {
                case REQUIREMENT_INTERPRETATION -> {
                    Map<String, String> answers = context.inputs().containsKey("clarification")
                            ? json.readValue(context.inputs().get("clarification").content(), new TypeReference<>() {}) : Map.of();
                    yield interpreter.interpret(context.requirement(), answers);
                }
                case AMBIGUITY_ANALYSIS -> {
                    var analysis = read(context, "requirement", RequirementAnalysis.class);
                    yield new IntelligenceModels.AmbiguityDecision(analysis.resolved(), analysis.questions(), false);
                }
                case REPOSITORY_ANALYSIS -> {
                    var snapshot = read(context, "snapshot", IntelligenceModels.SnapshotInput.class);
                    yield analyzer.analyze(snapshot.manifestHash(), snapshot.contents());
                }
                case PLANNING -> planner.plan(context.revisionId(), Hashes.sha256(context.requirement()),
                        read(context, "requirement", RequirementAnalysis.class), read(context, "repository", RepositoryMap.class));
                default -> throw new IllegalArgumentException("Engineering role not implemented in intelligence checkpoint");
            };
            return new ModelResponse("deterministic", "intelligence-v1", json.writeValueAsString(output), Duration.between(start, Instant.now()));
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("Invalid intelligence artifact schema", exception);
        }
    }
    private <T> T read(ExecutionContext context, String key, Class<T> type) throws java.io.IOException {
        if (!context.inputs().containsKey(key)) throw new IllegalArgumentException("Required stage input missing: " + key);
        return json.readValue(context.inputs().get(key).content(), type);
    }
}
