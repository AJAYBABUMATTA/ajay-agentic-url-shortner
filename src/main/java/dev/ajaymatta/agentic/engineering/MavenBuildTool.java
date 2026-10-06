package dev.ajaymatta.agentic.engineering;

import dev.ajaymatta.agentic.execution.*;
import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.w3c.dom.*;

/** A fixed Maven clean verify capability. No command is taken from agent output. */
@Component
public class MavenBuildTool implements EngineeringTool<Void,BuildEvidence> {
    private final TrustedBuildAssets assets;
    private final ProposalTool proposals;
    private final String localRepository;
    private final int timeoutSeconds;
    public MavenBuildTool(TrustedBuildAssets assets, ProposalTool proposals,
            @Value("${agentic.build.maven-repository:}") String localRepository,
            @Value("${agentic.build.timeout-seconds:180}") int timeoutSeconds) {
        this.assets=assets; this.proposals=proposals; this.localRepository=localRepository;
        if(timeoutSeconds<1 || timeoutSeconds>600) throw new IllegalArgumentException("Build timeout must be bounded");
        this.timeoutSeconds=timeoutSeconds;
    }
    @Override public Capability capability() { return Capability.MAVEN_CLEAN_VERIFY; }
    @Override public BuildEvidence execute(RepositoryWorkspace workspace,Void unused) {
        return executeControlled(workspace,()->false);
    }
    public BuildEvidence executeControlled(RepositoryWorkspace workspace,java.util.function.BooleanSupplier cancelled) {
        Instant started=Instant.now();
        var current=proposals.read(workspace);
        for(var asset:assets.buildFiles().entrySet()) if(!asset.getValue().equals(current.get(asset.getKey()))) throw new IllegalArgumentException("Untrusted build configuration or wrapper");
        // No repository-provided Maven extensions, settings or alternate toolchain hooks.
        if(current.keySet().stream().anyMatch(p -> p.startsWith(".mvn/") && !p.equals(".mvn/wrapper/maven-wrapper.properties"))) throw new IllegalArgumentException("Untrusted Maven extension");
        List<String> command=new ArrayList<>();
        if(System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win")) {
            command.addAll(List.of("powershell.exe","-NoProfile","-NonInteractive","-WindowStyle","Hidden","-ExecutionPolicy","Bypass","-Command"));
            String script="& './mvnw.cmd' '-B' '-ntp'";
            if(!localRepository.isBlank()) script+=" '"+("-Dmaven.repo.local="+Path.of(localRepository).toAbsolutePath()).replace("'","''")+"'";
            command.add(script+" 'clean' 'verify'; exit $LASTEXITCODE");
        } else {
            command.addAll(List.of("/bin/sh","./mvnw","-B","-ntp"));
            if(!localRepository.isBlank()) command.add("-Dmaven.repo.local="+Path.of(localRepository).toAbsolutePath());
            command.addAll(List.of("clean","verify"));
        }
        Process process=null;
        try {
            ProcessBuilder builder=new ProcessBuilder(command).directory(workspace.repository().toFile());
            var allowed=Set.of("PATH","JAVA_HOME","SYSTEMROOT","WINDIR","COMSPEC","PATHEXT","USERPROFILE","HOME","TMP","TEMP","APPDATA","LOCALAPPDATA");
            builder.environment().keySet().removeIf(key -> !allowed.contains(key.toUpperCase(Locale.ROOT)));
            process=builder.start();
            Process child=process;
            var threads=Executors.newVirtualThreadPerTaskExecutor();
            try {
                var stdout=threads.submit(()->bounded(child.getInputStream()));
                var stderr=threads.submit(()->bounded(child.getErrorStream()));
                boolean timedOut=false, stopped=false;
                while(!process.waitFor(200,TimeUnit.MILLISECONDS)) {
                    if(cancelled.getAsBoolean()) { stopped=true; break; }
                    if(Duration.between(started,Instant.now()).toSeconds()>=timeoutSeconds) { timedOut=true; break; }
                }
                if(timedOut || stopped) {
                    process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly();
                    process.waitFor(5,TimeUnit.SECONDS);
                }
                String out=stdout.get(10,TimeUnit.SECONDS), err=stderr.get(10,TimeUnit.SECONDS);
                int exit=timedOut || stopped ? -1 : process.exitValue();
                if(stopped) return new BuildEvidence(exit,Duration.between(started,Instant.now()),false,out,err+"\nCancelled by governed stop request",List.of(),List.of(),List.of(),new BuildEvidence.Coverage(false,0,0,null),BuildEvidence.FailureClassification.INFRASTRUCTURE);
                return reports(workspace,exit,Duration.between(started,Instant.now()),timedOut,out,err);
            } finally {
                if(child.isAlive()) { child.descendants().forEach(ProcessHandle::destroyForcibly); child.destroyForcibly(); }
                child.getInputStream().close(); child.getErrorStream().close();
                threads.shutdownNow(); threads.close();
            }
        } catch(InterruptedException failure) {
            Thread.currentThread().interrupt();
            return failed(started,BuildEvidence.FailureClassification.INFRASTRUCTURE,"Build interrupted");
        } catch(Exception failure) { return failed(started,BuildEvidence.FailureClassification.INFRASTRUCTURE,"Build capability failed: "+failure.getClass().getSimpleName()); }
        finally { if(process!=null && process.isAlive()) { process.descendants().forEach(ProcessHandle::destroyForcibly); process.destroyForcibly(); } }
    }
    private static BuildEvidence failed(Instant start,BuildEvidence.FailureClassification classification,String error) {
        return new BuildEvidence(-1,Duration.between(start,Instant.now()),false,"",error,List.of(),List.of(),List.of(),new BuildEvidence.Coverage(false,0,0,null),classification);
    }
    private static String bounded(InputStream stream) throws IOException {
        // Keep draining after the retained prefix fills, avoiding blocked child pipes.
        ByteArrayOutputStream retained=new ByteArrayOutputStream(); byte[] buffer=new byte[8192]; int read;
        while((read=stream.read(buffer))!=-1) {
            int keep=Math.min(read,60000-retained.size()); if(keep>0) retained.write(buffer,0,keep);
        }
        return retained.toString(java.nio.charset.StandardCharsets.UTF_8);
    }
    BuildEvidence reports(RepositoryWorkspace workspace,int exit,Duration duration,boolean timedOut,String stdout,String stderr) throws Exception {
        Path root=workspace.repository();
        List<String> compiled=new ArrayList<>(), discovered=new ArrayList<>(),failed=new ArrayList<>();
        Path compiler=root.resolve("target/maven-status/maven-compiler-plugin/compile/default-compile/inputFiles.lst");
        if(Files.isRegularFile(compiler)) {
            if(Files.size(compiler)>1048576) throw new IllegalArgumentException("Compiler report too large");
            for(String line:Files.readAllLines(compiler)) {
                Path path=Path.of(line).toAbsolutePath().normalize();
                if(path.startsWith(root) && path.toString().endsWith(".java")) {
                    String relative=root.relativize(path).toString().replace('\\','/');
                    if(relative.startsWith("src/main/java/") && Files.isRegularFile(root.resolve("target/classes/"+relative.substring("src/main/java/".length()).replace(".java",".class")))) compiled.add(relative);
                }
            }
        }
        Path reports=root.resolve("target/surefire-reports");
        if(Files.isDirectory(reports)) try(var files=Files.list(reports)) {
            var list=files.filter(p -> p.getFileName().toString().matches("TEST-.*\\.xml")).sorted().toList();
            if(list.size()>100) throw new IllegalArgumentException("Too many test reports");
            for(Path path:list) {
                NodeList cases=xml(path).getElementsByTagName("testcase");
                if(cases.getLength()>10000) throw new IllegalArgumentException("Too many test cases");
                for(int i=0;i<cases.getLength();i++) {
                    Element test=(Element)cases.item(i);
                    if(test.getElementsByTagName("skipped").getLength()>0) continue;
                    String name=test.getAttribute("classname")+"#"+test.getAttribute("name");
                    discovered.add(name);
                    if(test.getElementsByTagName("failure").getLength()>0 || test.getElementsByTagName("error").getLength()>0) failed.add(name);
                }
            }
        }
        var coverage=new BuildEvidence.Coverage(false,0,0,null);
        Path jacoco=root.resolve("target/site/jacoco/jacoco.xml");
        if(Files.isRegularFile(jacoco)) {
            var report=xml(jacoco); NodeList counters=report.getDocumentElement().getChildNodes();
            for(int i=0;i<counters.getLength();i++) if(counters.item(i) instanceof Element counter && counter.getTagName().equals("counter") && counter.getAttribute("type").equals("LINE")) {
                coverage=new BuildEvidence.Coverage(true,Long.parseLong(counter.getAttribute("covered")),Long.parseLong(counter.getAttribute("missed")),jacoco.toString());
            }
        }
        var classification=timedOut ? BuildEvidence.FailureClassification.TIMEOUT : !failed.isEmpty() ? BuildEvidence.FailureClassification.TEST
                : exit==0 && !compiled.isEmpty() && !discovered.isEmpty() && coverage.available() ? BuildEvidence.FailureClassification.NONE
                : stdout.contains("COMPILATION ERROR") ? BuildEvidence.FailureClassification.COMPILATION
                : stdout.contains("Could not resolve") || stdout.contains("Non-resolvable") ? BuildEvidence.FailureClassification.DEPENDENCY
                : BuildEvidence.FailureClassification.UNKNOWN;
        return new BuildEvidence(exit,duration,timedOut,stdout,stderr,compiled,discovered,failed,coverage,classification);
    }
    private static Document xml(Path path) throws Exception {
        if(Files.isSymbolicLink(path) || Files.size(path)>4194304) throw new IllegalArgumentException("Unsafe build report");
        var factory=DocumentBuilderFactory.newInstance();
        factory.setFeature("http://xml.org/sax/features/external-general-entities",false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities",false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD,""); factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA,"");
        return factory.newDocumentBuilder().parse(path.toFile());
    }
}
