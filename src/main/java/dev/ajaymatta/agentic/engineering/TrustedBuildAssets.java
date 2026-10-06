package dev.ajaymatta.agentic.engineering;

import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Platform-owned wrapper assets, never supplied by a caller/model/repository. */
@Component
public class TrustedBuildAssets {
    private final Map<String,String> files;
    public TrustedBuildAssets(@Value("${agentic.build.assets-root:.}") String root) {
        try {
            Path base=Path.of(root).toAbsolutePath().normalize();
            var loaded=new LinkedHashMap<String,String>();
            for(String path:List.of("mvnw","mvnw.cmd",".mvn/wrapper/maven-wrapper.properties")) {
                Path file=base.resolve(path);
                if (Files.isSymbolicLink(file) || Files.size(file)>65536) throw new IllegalArgumentException("Invalid trusted wrapper asset");
                loaded.put(path,Files.readString(file));
            }
            files=Map.copyOf(loaded);
        } catch(java.io.IOException failure) { throw new IllegalStateException("Platform wrapper assets are unavailable",failure); }
    }
    public Map<String,String> files() { return files; }
    public Map<String,String> buildFiles() {
        var result=new LinkedHashMap<>(files); result.put("pom.xml",GeneratedServiceSources.POM); return Map.copyOf(result);
    }
}
