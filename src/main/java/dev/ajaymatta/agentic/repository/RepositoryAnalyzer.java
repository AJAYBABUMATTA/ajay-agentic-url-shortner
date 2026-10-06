package dev.ajaymatta.agentic.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class RepositoryAnalyzer {
    private static final Pattern CLASS = Pattern.compile("\\b(?:class|record|interface)\\s+(\\w+)");
    private static final Pattern ROUTE = Pattern.compile("@(Get|Post|Put|Delete|Patch|Request)Mapping\\s*(?:\\(\\s*(?:value\\s*=\\s*)?\"([^\"]*)\")?");

    public RepositoryMap analyze(String manifestHash, Map<String, String> contents) {
        List<RepositoryMap.Component> components = new ArrayList<>();
        List<String> tests = new ArrayList<>();
        List<String> builds = new ArrayList<>();
        for (var entry : contents.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            String path = entry.getKey(), source = entry.getValue();
            if (path.endsWith("pom.xml") || path.endsWith("mvnw") || path.endsWith("mvnw.cmd")) builds.add(path);
            if (path.startsWith("src/test/")) tests.add(path);
            if (!path.startsWith("src/main/java/") || !path.endsWith(".java")) continue;
            var type = CLASS.matcher(source);
            if (!type.find()) continue;
            String name = type.group(1);
            String kind = source.contains("@RestController") ? "controller"
                    : source.contains("@Service") ? "service"
                    : source.contains("@Repository") || source.contains("JpaRepository") ? "repository"
                    : source.contains("@Entity") || source.contains("record ") ? "domain" : "component";
            List<String> routes = new ArrayList<>();
            var route = ROUTE.matcher(source);
            while (route.find()) routes.add(route.group(1) + " " + (route.group(2) == null ? "" : route.group(2)));
            components.add(new RepositoryMap.Component(path, name, kind, routes));
        }
        List<RepositoryMap.DataFlow> flows = new ArrayList<>();
        for (var from : components) for (var to : components) {
            if (!from.equals(to) && Pattern.compile("\\b" + Pattern.quote(to.name()) + "\\b")
                    .matcher(contents.get(from.path())).find()) {
                flows.add(new RepositoryMap.DataFlow(from.path(), to.path(), "Source references type " + to.name()));
            }
        }
        return new RepositoryMap(manifestHash, components.isEmpty(), components, flows, builds, tests,
                List.of("Bounded static source analysis; type references are impact candidates, not proven runtime execution",
                        "Generated compilation and HTTP integration tests must verify runtime connectivity"));
    }
}
