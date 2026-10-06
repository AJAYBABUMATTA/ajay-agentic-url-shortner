package dev.ajaymatta.agentic.engineering;

import dev.ajaymatta.agentic.execution.*;
import dev.ajaymatta.agentic.intelligence.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class DeterministicEngineeringProvider implements ModelProvider {
    private final IntelligenceStore store;
    private final TrustedBuildAssets assets;
    public DeterministicEngineeringProvider(IntelligenceStore store, TrustedBuildAssets assets) { this.store=store; this.assets=assets; }
    @Override public ModelResponse generate(ModelRequest request) {
        var context=request.context();
        var input=store.decode(context.inputs().get("task").content(),EngineeringModels.TaskInput.class);
        var analysis=store.decode(context.inputs().get("requirement").content(),RequirementAnalysis.class);
        int redirectStatus=analysis.criteria().stream().filter(c -> c.capability().equals("redirect"))
                .anyMatch(c -> c.description().contains("301")) ? 301 : 302;
        Object output;
        if(request.role()==AgentRole.IMPLEMENTATION || request.role()==AgentRole.TESTING) {
            Map<String,String> files=new LinkedHashMap<>();
            switch(input.task().key()) {
                case "implement-create" -> {
                    files.putAll(assets.buildFiles());
                    files.put(GeneratedServiceSources.ROOT+"ShortenerApplication.java",GeneratedServiceSources.APPLICATION);
                    files.put(GeneratedServiceSources.ROOT+"UrlStore.java",GeneratedServiceSources.STORE);
                    files.put(GeneratedServiceSources.ROOT+"UrlController.java",GeneratedServiceSources.CREATE);
                }
                case "implement-redirect" -> files.put(GeneratedServiceSources.ROOT+"RedirectController.java",GeneratedServiceSources.redirect(redirectStatus));
                case "test-create" -> files.put(GeneratedServiceSources.TEST+"CreateUrlTest.java",GeneratedServiceSources.CREATE_TEST);
                case "test-redirect" -> files.put(GeneratedServiceSources.TEST+"RedirectUrlTest.java",GeneratedServiceSources.redirectTest(redirectStatus));
                default -> throw new IllegalArgumentException("No verified generator for this task");
            }
            output=new EngineeringModels.Proposal(files.entrySet().stream().map(entry -> new FileOperation(
                    input.baseline().containsKey(entry.getKey()) ? FileOperation.Operation.UPDATE : FileOperation.Operation.CREATE,
                    entry.getKey(),entry.getValue(),input.baseline().containsKey(entry.getKey()) ? Hashes.sha256(input.baseline().get(entry.getKey())) : null,
                    "Implement "+input.task().key()+" in the isolated service",context.revisionId().toString(),input.task().criterionIds(),context.taskId(),context.inputHashes())).toList());
        } else {
            String decision=switch(request.role()) {
                case ARCHITECTURE -> "Spring Boot HTTP controllers call a shared concurrent UrlStore; ten-character SecureRandom codes use collision-safe putIfAbsent. Create returns 201; redirect uses the requested status and exact Location.";
                case SECURITY_RISK -> "Bounded create/redirect slice: validate HTTP(S), host, credentials and length; build uses a pinned platform-owned POM and wrapper. Production security and persistence remain incomplete.";
                case DOCUMENTATION -> "Run the generated service using ./mvnw spring-boot:run (Windows: .\\mvnw.cmd spring-boot:run). POST /api/v1/urls with target; GET /{code}. Generated HTTP tests exercise creation, redirects and invalid inputs.";
                default -> throw new IllegalArgumentException("Unsupported engineering specialist");
            };
            if(input.task().key().equals("security-review")) {
                String production=context.inputs().values().stream().filter(a->a.type()==EngineeringArtifact.ArtifactType.FILE_PROPOSAL)
                        .flatMap(a->store.decode(a.content(),EngineeringModels.Proposal.class).operations().stream())
                        .filter(o->o.path().startsWith("src/main/java/")).map(FileOperation::content).reduce("",String::concat);
                for(String required:List.of("SecureRandom","putIfAbsent","getHost()","getUserInfo()","length()>2048","https")) {
                    if(!production.contains(required)) throw new IllegalArgumentException("Generated slice lacks required security control: "+required);
                }
                decision+=" Inspected generated production proposals for SecureRandom, collision-safe insertion, HTTP(S), host, credential and length checks. Static token checks alone are not a complete security assessment.";
            }
            if(request.role()==AgentRole.DOCUMENTATION) {
                var build=context.inputs().values().stream().filter(a->a.type()==EngineeringArtifact.ArtifactType.BUILD_EVIDENCE).findFirst().orElseThrow();
                var result=store.decode(build.content(),BuildEvidence.class);
                decision+=" Verified build artifact "+build.sha256()+" compiled "+result.compiledProductionPaths().size()+" production files and executed "+result.discoveredTests().size()+" tests; coverage report: "+result.coverage().reportLocation();
            }
            output=new EngineeringModels.Decision(decision,input.task().criterionIds(),context.inputHashes(),
                    List.of("In-memory storage resets on restart", "Expiry, aliases, analytics, rate limits and production URL security are deferred", "Release approval and feature-completion governance are not enabled in this slice"));
        }
        return new ModelResponse("deterministic","engineering-v1",store.encode(output),Duration.ZERO);
    }
}
