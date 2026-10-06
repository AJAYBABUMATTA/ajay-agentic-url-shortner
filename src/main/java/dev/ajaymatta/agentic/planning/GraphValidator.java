package dev.ajaymatta.agentic.planning;

import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class GraphValidator {
    public Map<String, List<String>> validate(List<EngineeringPlan.PlannedTask> tasks) {
        if (tasks.isEmpty()) throw new IllegalArgumentException("Empty task graph");
        Map<String, EngineeringPlan.PlannedTask> indexed = new LinkedHashMap<>();
        for (var task : tasks) {
            if (task.key() == null || task.key().isBlank() || task.role() == null || task.exitGates().isEmpty()
                    || indexed.putIfAbsent(task.key(), task) != null) throw new IllegalArgumentException("Invalid or duplicate task");
            if (new HashSet<>(task.dependencies()).size() != task.dependencies().size()) throw new IllegalArgumentException("Duplicate dependency");
        }
        for (var task : tasks) for (String dependency : task.dependencies()) {
            if (!indexed.containsKey(dependency) || dependency.equals(task.key())) throw new IllegalArgumentException("Missing or self dependency");
        }
        Map<String, Integer> depths = new LinkedHashMap<>();
        for (String key : indexed.keySet()) depth(key, indexed, depths, new HashSet<>());
        Map<String, List<String>> layers = new TreeMap<>();
        depths.forEach((key, value) -> layers.computeIfAbsent("layer-" + value, ignored -> new ArrayList<>()).add(key));
        Map<String, List<String>> immutable = new TreeMap<>();
        layers.forEach((key, value) -> immutable.put(key, List.copyOf(value)));
        return Collections.unmodifiableMap(immutable);
    }
    private int depth(String key, Map<String, EngineeringPlan.PlannedTask> tasks, Map<String, Integer> depths, Set<String> visiting) {
        if (depths.containsKey(key)) return depths.get(key);
        if (!visiting.add(key)) throw new IllegalArgumentException("Dependency cycle");
        int value = 0;
        for (String dependency : tasks.get(key).dependencies()) value = Math.max(value, depth(dependency, tasks, depths, visiting) + 1);
        visiting.remove(key); depths.put(key, value); return value;
    }
}
