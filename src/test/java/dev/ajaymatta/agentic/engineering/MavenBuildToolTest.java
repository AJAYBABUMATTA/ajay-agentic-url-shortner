package dev.ajaymatta.agentic.engineering;

import dev.ajaymatta.agentic.execution.*;
import dev.ajaymatta.agentic.repository.RepositoryTools;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class MavenBuildToolTest {
    @TempDir Path temp;
    RepositoryWorkspace workspace;
    MavenBuildTool tool;
    @BeforeEach void setup() throws Exception {
        Path root=Files.createDirectories(temp.resolve("workspaces/repo"));
        workspace=new RepositoryWorkspace(UUID.randomUUID(),UUID.randomUUID(),root,temp.resolve("workspaces/baseline"),Hashes.sha256("baseline"));
        tool=new MavenBuildTool(new TrustedBuildAssets("."),new ProposalTool(new RepositoryTools(temp.resolve("sources").toString(),temp.resolve("workspaces").toString(),100,262144,10485760)),"",30);
    }
    @Test void zeroExitWithoutDiscoveredTestsIsNotSuccess() throws Exception {
        var build=tool.reports(workspace,0,Duration.ofSeconds(1),false,"BUILD SUCCESS","");
        assertThat(build.classification()).isEqualTo(BuildEvidence.FailureClassification.UNKNOWN);
    }
    @Test void parsersDiscoverExecutedCasesRejectSkippedAndCollectFailures() throws Exception {
        write("target/surefire-reports/TEST-app.Test.xml","<testsuite><testcase classname='app.Test' name='passes'/><testcase classname='app.Test' name='fails'><failure/></testcase><testcase classname='app.Test' name='skips'><skipped/></testcase></testsuite>");
        var build=tool.reports(workspace,1,Duration.ofSeconds(1),false,"Tests failed","");
        assertThat(build.discoveredTests()).containsExactly("app.Test#passes","app.Test#fails");
        assertThat(build.failedTests()).containsExactly("app.Test#fails");
        assertThat(build.classification()).isEqualTo(BuildEvidence.FailureClassification.TEST);
    }
    @Test void compiledPathsRequireActualClassOutputAndCoverageComesFromReport() throws Exception {
        Path source=workspace.repository().resolve("src/main/java/app/Service.java");
        write("target/maven-status/maven-compiler-plugin/compile/default-compile/inputFiles.lst",source+"\n");
        write("target/classes/app/Service.class","placeholder for parser test");
        write("target/surefire-reports/TEST-app.Test.xml","<testsuite><testcase classname='app.Test' name='passes'/></testsuite>");
        write("target/site/jacoco/jacoco.xml","<!DOCTYPE report PUBLIC '-//JACOCO//DTD Report 1.1//EN' 'report.dtd'><report><counter type='LINE' covered='10' missed='2'/></report>");
        var build=tool.reports(workspace,0,Duration.ofSeconds(1),false,"BUILD SUCCESS","");
        assertThat(build.compiledProductionPaths()).containsExactly("src/main/java/app/Service.java");
        assertThat(build.coverage().coveredLines()).isEqualTo(10);
        assertThat(build.classification()).isEqualTo(BuildEvidence.FailureClassification.NONE);
        Files.delete(workspace.repository().resolve("target/classes/app/Service.class"));
        assertThat(tool.reports(workspace,0,Duration.ZERO,false,"","").compiledProductionPaths()).isEmpty();
    }
    @Test void timeoutAndCompilerFailureAreClassified() throws Exception {
        assertThat(tool.reports(workspace,-1,Duration.ZERO,true,"","").classification()).isEqualTo(BuildEvidence.FailureClassification.TIMEOUT);
        assertThat(tool.reports(workspace,1,Duration.ZERO,false,"COMPILATION ERROR","").classification()).isEqualTo(BuildEvidence.FailureClassification.COMPILATION);
    }
    @Test void modifiedBuildFilesCannotStartAChildProcess() {
        assertThatThrownBy(()->tool.execute(workspace,null)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Untrusted build");
    }
    private void write(String path,String content) throws Exception { Path file=workspace.repository().resolve(path); Files.createDirectories(file.getParent()); Files.writeString(file,content); }
}
