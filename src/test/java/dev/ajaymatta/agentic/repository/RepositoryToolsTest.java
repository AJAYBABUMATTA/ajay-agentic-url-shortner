package dev.ajaymatta.agentic.repository;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RepositoryToolsTest {
    @TempDir Path temp;
    @ParameterizedTest @ValueSource(strings={"../outside", "/absolute", "C:/outside", "folder\\escape", "a/../b", "a//b", "a/./b", "."})
    void unsafeSelectorsRejectedBeforeAnyFileAccess(String selector) {
        assertThatThrownBy(() -> RepositoryTools.validateSelector(selector)).isInstanceOf(RepositoryTools.RepositoryPolicyException.class);
    }
    @Test void snapshotsAreIsolatedVerifiedAndDoNotModifySource() throws Exception {
        var source = fixture("repo"); Files.writeString(source.resolve("Service.java"), "class Service {}\n");
        var tools = tools(10, 1000, 10000);
        var before = tools.readTree(source);
        var snapshot = tools.snapshot("repo", UUID.randomUUID(), UUID.randomUUID());
        assertThat(tools.readTree(source)).isEqualTo(before);
        assertThat(tools.readTree(snapshot.workspace().baseline())).isEqualTo(before);
        assertThat(tools.readTree(snapshot.workspace().repository())).isEqualTo(before);
        Files.writeString(snapshot.workspace().repository().resolve("Service.java"), "class Changed {}\n");
        assertThat(tools.readTree(source)).isEqualTo(before);
        assertThat(tools.readTree(snapshot.workspace().baseline())).isEqualTo(before);
    }
    @Test void targetSourcePackageIsAnalyzedWhileMavenOutputIsExcluded() throws Exception {
        var source = fixture("repo");
        Files.writeString(source.resolve("pom.xml"), "<project/>");
        var pkg = Files.createDirectories(source.resolve("src/main/java/example/target"));
        Files.writeString(pkg.resolve("UrlController.java"), "@RestController class UrlController { UrlService service; }");
        Files.writeString(pkg.resolve("UrlService.java"), "@Service class UrlService {}");
        Files.writeString(Files.createDirectories(source.resolve("target")).resolve("output.class"), "build output");
        var module = Files.createDirectories(source.resolve("module"));
        Files.writeString(module.resolve("pom.xml"), "<project/>");
        Files.writeString(Files.createDirectories(module.resolve("target")).resolve("output.class"), "module output");
        var snapshot = tools(10,1000,10000).snapshot("repo", UUID.randomUUID(), UUID.randomUUID());
        assertThat(snapshot.contents()).containsKeys("src/main/java/example/target/UrlController.java", "src/main/java/example/target/UrlService.java");
        assertThat(snapshot.contents().keySet()).noneMatch(path -> path.endsWith(".class"));
        var analysis = new RepositoryAnalyzer().analyze(snapshot.workspace().baselineManifestHash(), snapshot.contents());
        assertThat(analysis.greenfield()).isFalse();
        assertThat(analysis.dataFlows()).anyMatch(flow -> flow.fromPath().endsWith("UrlController.java") && flow.toPath().endsWith("UrlService.java"));
    }
    @Test void limitsRejectFileCountIndividualSizeAndAggregateSize() throws Exception {
        var source = fixture("repo"); Files.writeString(source.resolve("A.java"), "123456"); Files.writeString(source.resolve("B.java"), "123456");
        assertThatThrownBy(() -> tools(1,100,100).snapshot("repo", UUID.randomUUID(), UUID.randomUUID())).isInstanceOf(RepositoryTools.RepositoryPolicyException.class);
        assertThatThrownBy(() -> tools(10,5,100).snapshot("repo", UUID.randomUUID(), UUID.randomUUID())).isInstanceOf(RepositoryTools.RepositoryPolicyException.class);
        assertThatThrownBy(() -> tools(10,100,10).snapshot("repo", UUID.randomUUID(), UUID.randomUUID())).isInstanceOf(RepositoryTools.RepositoryPolicyException.class);
    }
    @Test void unsupportedFilesRejectedAndExcludedSecretsNotCopied() throws Exception {
        var source = fixture("repo"); Files.writeString(source.resolve(".env"), "PRIVATE=secret"); Files.writeString(source.resolve("README.md"), "fixture");
        var snapshot = tools(10,100,1000).snapshot("repo", UUID.randomUUID(), UUID.randomUUID());
        assertThat(snapshot.contents()).containsOnlyKeys("README.md");
        Files.writeString(source.resolve("image.exe"), "untrusted");
        assertThatThrownBy(() -> tools(10,100,1000).snapshot("repo", UUID.randomUUID(), UUID.randomUUID())).isInstanceOf(RepositoryTools.RepositoryPolicyException.class);
    }
    @Test void literalSearchIsBoundedAndInvalidQueriesRejected() throws Exception {
        var source = fixture("repo"); Files.writeString(source.resolve("README.md"), "match\nmatch\nmatch\n");
        var tools = tools(10,100,1000); var snapshot = tools.snapshot("repo", UUID.randomUUID(), UUID.randomUUID());
        assertThat(tools.search(snapshot,"match",2)).containsExactly("README.md:1","README.md:2");
        assertThatThrownBy(() -> tools.search(snapshot,"match",101)).isInstanceOf(RepositoryTools.RepositoryPolicyException.class);
    }
    @Test void symbolicLinkEscapeIsRejectedWhenHostCanCreateLinks() throws Exception {
        var source = fixture("repo");
        var outside = Files.createDirectories(temp.resolve("outside"));
        Files.writeString(outside.resolve("Outside.java"),"class Outside {}");
        var link = source.resolve("escape");
        try { Files.createSymbolicLink(link, outside); }
        catch (IOException | UnsupportedOperationException exception) {
            if (System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")) {
                // Fixed test-only capability. Both locations are owned by this JUnit temp directory.
                String command = "$ErrorActionPreference='Stop'; New-Item -ItemType Junction -Path '"
                        + link.toString().replace("'", "''") + "' -Target '" + outside.toString().replace("'", "''") + "' | Out-Null";
                String encoded = java.util.Base64.getEncoder().encodeToString(command.getBytes(java.nio.charset.StandardCharsets.UTF_16LE));
                var process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden", "-EncodedCommand", encoded)
                        .redirectErrorStream(true).start();
                if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) { process.destroyForcibly(); assumeTrue(false, "Host junction creation timed out"); }
                assumeTrue(process.exitValue() == 0, "Host permits neither symbolic links nor temporary junctions");
            } else assumeTrue(false, "Host requires symlink privileges: " + exception.getClass().getSimpleName());
        }
        assertThatThrownBy(() -> tools(10,1000,10000).snapshot("repo",UUID.randomUUID(),UUID.randomUUID())).isInstanceOf(RepositoryTools.RepositoryPolicyException.class);
        assertThat(Files.readString(outside.resolve("Outside.java"))).isEqualTo("class Outside {}");
    }
    private Path fixture(String name) throws IOException { return Files.createDirectories(temp.resolve("sources").resolve(name)); }
    private RepositoryTools tools(int count, long size, long total) { return new RepositoryTools(temp.resolve("sources").toString(),temp.resolve("workspaces").toString(),count,size,total); }
}
