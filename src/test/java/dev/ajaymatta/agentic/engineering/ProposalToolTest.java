package dev.ajaymatta.agentic.engineering;

import dev.ajaymatta.agentic.execution.*;
import dev.ajaymatta.agentic.repository.RepositoryTools;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class ProposalToolTest {
    @TempDir Path temp;
    RepositoryWorkspace workspace;
    RepositoryTools repositories;
    UUID task=UUID.randomUUID();
    @BeforeEach void setup() throws Exception {
        Path repo=Files.createDirectories(temp.resolve("workspaces/repo")), baseline=Files.createDirectories(temp.resolve("workspaces/baseline"));
        repositories=new RepositoryTools(temp.resolve("sources").toString(),temp.resolve("workspaces").toString(),100,262144,10485760);
        Files.writeString(repo.resolve("README.md"),"baseline\n"); Files.writeString(baseline.resolve("README.md"),"baseline\n");
        workspace=new RepositoryWorkspace(UUID.randomUUID(),UUID.randomUUID(),repo,baseline,ProposalTool.manifest(Map.of("README.md","baseline\n")));
    }
    @Test void exactCreateUpdateDeleteProducesManifestDiffAndPreservesBaseline() throws Exception {
        var tool=new ProposalTool(repositories);
        var patch=tool.execute(workspace,List.of(create("src/main/java/app/Service.java","class Service {}\n"),update("README.md","baseline\n","updated\n")));
        assertThat(patch.afterHash()).isEqualTo(ProposalTool.manifest(tool.read(workspace)));
        assertThat(patch.unifiedDiff()).contains("+++ b/src/main/java/app/Service.java","-baseline","+updated");
        assertThat(Files.readString(workspace.baseline().resolve("README.md"))).isEqualTo("baseline\n");
        tool.execute(workspace,List.of(new FileOperation(FileOperation.Operation.DELETE,"src/main/java/app/Service.java",null,Hashes.sha256("class Service {}\n"),"Remove obsolete class",workspace.revisionId().toString(),List.of("AC-CREATE"),task,List.of(Hashes.sha256("input")))));
        assertThat(Files.exists(workspace.repository().resolve("src/main/java/app/Service.java"))).isFalse();
    }
    @Test void duplicateTraversalUnsupportedAndStaleProposalsCannotMutateFiles() {
        var tool=new ProposalTool(repositories); var before=tool.read(workspace);
        for(var operations:List.of(List.of(create("../outside.java","x")),List.of(create("src/main/java/app/A.java","x"),create("src/main/java/app/A.java","y")),
                List.of(create("scripts/run.ps1","danger")),List.of(update("README.md","wrong","new")),List.of(create("README.md","existing")))) {
            assertThatThrownBy(()->tool.execute(workspace,operations)).isInstanceOf(IllegalArgumentException.class);
            assertThat(tool.read(workspace)).isEqualTo(before);
        }
    }
    @Test void oversizedAndWrongRevisionOperationsAreRejected() {
        var tool=new ProposalTool(repositories);
        assertThatThrownBy(()->tool.execute(workspace,List.of(create("src/main/java/app/A.java","x".repeat(262145))))).isInstanceOf(IllegalArgumentException.class);
        var operation=new FileOperation(FileOperation.Operation.CREATE,"src/main/java/app/A.java","x",null,"Wrong revision",UUID.randomUUID().toString(),List.of("AC-CREATE"),task,List.of(Hashes.sha256("input")));
        assertThatThrownBy(()->tool.execute(workspace,List.of(operation))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void partialBatchFailureRestoresOriginalContents() {
        var tool=new ProposalTool(repositories) {
            int calls;
            @Override public void validate(RepositoryWorkspace workspace,List<FileOperation> operations) {
                if(++calls==3) throw new IllegalStateException("Controlled interruption after first write");
                super.validate(workspace,operations);
            }
        };
        var before=tool.read(workspace);
        assertThatThrownBy(()->tool.execute(workspace,List.of(update("README.md","baseline\n","new\n"),create("src/main/java/app/A.java","class A {}"))))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("restoration attempted");
        assertThat(tool.read(workspace)).isEqualTo(before);
    }
    private FileOperation create(String path,String content) { return new FileOperation(FileOperation.Operation.CREATE,path,content,null,"Generate source",workspace.revisionId().toString(),List.of("AC-CREATE"),task,List.of(Hashes.sha256("input"))); }
    private FileOperation update(String path,String old,String content) { return new FileOperation(FileOperation.Operation.UPDATE,path,content,Hashes.sha256(old),"Update source",workspace.revisionId().toString(),List.of("AC-CREATE"),task,List.of(Hashes.sha256("input"))); }
}
