package dev.ajaymatta.agentic.planning;

import dev.ajaymatta.agentic.execution.AgentRole;
import dev.ajaymatta.agentic.workflow.domain.Gate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record EngineeringPlan(UUID revisionId, String requirementHash, String repositoryHash,
                              List<PlannedTask> tasks, Map<String, List<String>> executionLayers,
                              List<String> decisions) {
    public EngineeringPlan {
        tasks = List.copyOf(tasks);
        var layers = new java.util.TreeMap<String, List<String>>();
        executionLayers.forEach((key, value) -> layers.put(key, List.copyOf(value)));
        executionLayers = java.util.Collections.unmodifiableMap(layers); decisions = List.copyOf(decisions);
    }
    public record PlannedTask(String key, AgentRole role, List<String> dependencies, List<String> criterionIds,
                              List<String> impactedPaths, List<Gate> entryGates, List<Gate> exitGates) {
        public PlannedTask {
            dependencies = List.copyOf(dependencies); criterionIds = List.copyOf(criterionIds);
            impactedPaths = List.copyOf(impactedPaths); entryGates = List.copyOf(entryGates); exitGates = List.copyOf(exitGates);
        }
    }
}
