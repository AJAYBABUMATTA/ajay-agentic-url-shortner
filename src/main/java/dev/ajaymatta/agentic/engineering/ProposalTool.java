package dev.ajaymatta.agentic.engineering;

import dev.ajaymatta.agentic.execution.*;
import dev.ajaymatta.agentic.repository.RepositoryTools;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ProposalTool implements EngineeringTool<List<FileOperation>,EngineeringModels.AppliedPatch> {
    private final RepositoryTools repositories;
    public ProposalTool(RepositoryTools repositories) { this.repositories=repositories; }
    @Override public Capability capability() { return Capability.APPLY_PROPOSAL; }
    public static String manifest(Map<String,String> contents) {
        return Hashes.sha256(contents.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey()+"\t"+e.getValue().getBytes(StandardCharsets.UTF_8).length+"\t"+Hashes.sha256(e.getValue())+"\n").reduce("",String::concat));
    }
    public Map<String,String> read(RepositoryWorkspace workspace) {
        try { return repositories.readTree(workspace.repository()); }
        catch(java.io.IOException failure) { throw new IllegalStateException("Cannot inspect workspace",failure); }
    }
    public void validate(RepositoryWorkspace workspace, List<FileOperation> operations) {
        validateAgainst(workspace,operations,read(workspace));
    }
    public void validateAgainst(RepositoryWorkspace workspace,List<FileOperation> operations,Map<String,String> current) {
        if(operations.isEmpty() || operations.size()>100) throw new IllegalArgumentException("Proposal count exceeds policy");
        var paths=new HashSet<String>(); long total=0;
        for(var operation:operations) {
            if(!paths.add(operation.path())) throw new IllegalArgumentException("Duplicate proposal path");
            destination(workspace,operation.path());
            if(!operation.requirementId().equals(workspace.revisionId().toString())) throw new IllegalArgumentException("Stale proposal revision");
            int bytes=operation.content()==null ? 0 : operation.content().getBytes(StandardCharsets.UTF_8).length;
            total+=bytes;
            if(bytes>262144 || total>1048576) throw new IllegalArgumentException("Proposal size exceeds policy");
            String previous=current.get(operation.path());
            if(operation.type()==FileOperation.Operation.CREATE ? previous!=null
                    : previous==null || !Hashes.sha256(previous).equals(operation.expectedSha256())) throw new IllegalArgumentException("Optimistic file hash conflict");
        }
    }
    @Override public EngineeringModels.AppliedPatch execute(RepositoryWorkspace workspace,List<FileOperation> operations) {
        validate(workspace,operations);
        var before=read(workspace); var expected=new TreeMap<>(before); var applied=new ArrayList<FileOperation>();
        StringBuilder diff=new StringBuilder();
        try {
            for(var operation:operations) {
                // Recheck location and optimistic content immediately before each mutation.
                validate(workspace,List.of(operation));
                Path path=destination(workspace,operation.path());
                String previous=before.get(operation.path());
                diff.append(diff(operation.path(),previous,operation.content()));
                applied.add(operation);
                if(operation.type()==FileOperation.Operation.DELETE) { Files.delete(path); expected.remove(operation.path()); }
                else { writeAtomic(path,operation.content()); expected.put(operation.path(),operation.content()); }
                if(operation.path().equals("mvnw") && operation.type()!=FileOperation.Operation.DELETE
                        && Files.getFileStore(path).supportsFileAttributeView("posix")) Files.setPosixFilePermissions(path,java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"));
            }
            if(!read(workspace).equals(expected)) throw new IllegalStateException("Applied files do not match exact proposal");
            Map<String,String> hashes=new TreeMap<>(); expected.forEach((path,content)->hashes.put(path,Hashes.sha256(content)));
            return new EngineeringModels.AppliedPatch(manifest(before),manifest(expected),Map.copyOf(hashes),diff.toString(),List.copyOf(operations));
        } catch(Exception failure) {
            try {
                Collections.reverse(applied);
                for(var operation:applied) {
                    Path path=destination(workspace,operation.path());
                    if(before.containsKey(operation.path())) writeAtomic(path,before.get(operation.path())); else Files.deleteIfExists(path);
                }
                if(!read(workspace).equals(before)) throw new IllegalStateException("Patch restoration mismatch");
            } catch(Exception restoration) { failure.addSuppressed(restoration); }
            throw new IllegalStateException("Patch failed; restoration attempted",failure);
        }
    }
    private static Path destination(RepositoryWorkspace workspace,String relative) {
        if(relative==null || !relative.matches("[a-zA-Z0-9_./-]+") || relative.startsWith("/")
                || Arrays.stream(relative.split("/",-1)).anyMatch(s -> s.isEmpty() || s.equals(".") || s.equals(".."))) throw new IllegalArgumentException("Unsafe proposal path");
        boolean allowed=relative.equals("pom.xml") || relative.equals("mvnw") || relative.equals("mvnw.cmd")
                || relative.equals(".mvn/wrapper/maven-wrapper.properties") || relative.equals("README.md")
                || relative.startsWith("src/main/java/") && relative.endsWith(".java")
                || relative.startsWith("src/test/java/") && relative.endsWith(".java")
                || Set.of("src/main/resources/application.yaml","src/main/resources/db/migration/V1__shortener.sql","src/test/resources/application-test.yaml").contains(relative);
        if(!allowed) throw new IllegalArgumentException("Unsupported proposal file type or root");
        Path root=workspace.repository().toAbsolutePath().normalize(), result=root.resolve(relative).normalize();
        if(!result.startsWith(root)) throw new IllegalArgumentException("Proposal escaped workspace");
        for(Path path=result;path!=null;path=path.getParent()) {
            try {
                if(Files.isSymbolicLink(path) || Files.exists(path,LinkOption.NOFOLLOW_LINKS) && !path.toRealPath().equals(path)) throw new IllegalArgumentException("Proposal link/junction rejected");
            } catch(java.io.IOException failure) { throw new IllegalArgumentException("Cannot verify proposal path",failure); }
        }
        return result;
    }
    private static void writeAtomic(Path path,String content) throws java.io.IOException {
        Files.createDirectories(path.getParent());
        Path temporary=Files.createTempFile(path.getParent(),".agentic-", ".tmp");
        try {
            Files.writeString(temporary,content,StandardCharsets.UTF_8,StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(temporary,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
    private static String diff(String path,String oldContent,String newContent) {
        var oldLines=oldContent==null ? List.<String>of() : oldContent.lines().toList();
        var newLines=newContent==null ? List.<String>of() : newContent.lines().toList();
        StringBuilder result=new StringBuilder("--- "+(oldContent==null ? "/dev/null" : "a/"+path)+"\n+++ "+(newContent==null ? "/dev/null" : "b/"+path)+"\n");
        result.append("@@ -").append(oldLines.isEmpty()?0:1).append(",").append(oldLines.size()).append(" +").append(newLines.isEmpty()?0:1).append(",").append(newLines.size()).append(" @@\n");
        oldLines.forEach(line->result.append('-').append(line).append('\n'));
        if(oldContent!=null && !oldContent.isEmpty() && !oldContent.endsWith("\n")) result.append("\\ No newline at end of file\n");
        newLines.forEach(line->result.append('+').append(line).append('\n'));
        if(newContent!=null && !newContent.isEmpty() && !newContent.endsWith("\n")) result.append("\\ No newline at end of file\n");
        return result.toString();
    }
}
