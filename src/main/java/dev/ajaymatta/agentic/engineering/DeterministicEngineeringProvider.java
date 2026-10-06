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
    private final FullShortenerSources fullSources;
    public DeterministicEngineeringProvider(IntelligenceStore store, TrustedBuildAssets assets) { this(store,assets,new FullShortenerSources(".")); }
    @org.springframework.beans.factory.annotation.Autowired
    public DeterministicEngineeringProvider(IntelligenceStore store, TrustedBuildAssets assets,FullShortenerSources fullSources) { this.store=store; this.assets=assets; this.fullSources=fullSources; }
    @Override public ModelResponse generate(ModelRequest request) {
        var context=request.context();
        var input=store.decode(context.inputs().get("task").content(),EngineeringModels.TaskInput.class);
        var analysis=store.decode(context.inputs().get("requirement").content(),RequirementAnalysis.class);
        int redirectStatus=analysis.criteria().stream().filter(c -> c.capability().equals("redirect"))
                .anyMatch(c -> c.description().contains("301")) ? 301 : 302;
        Object output;
        boolean full=input.task().impactedPaths().stream().anyMatch(p->p.startsWith(FullShortenerSources.ROOT) || p.startsWith(FullShortenerSources.TEST)) || input.baseline().containsKey(FullShortenerSources.ROOT+"UrlCapabilities.java") || !input.baseline().containsKey(BrownfieldSources.ROOT+"TargetApplication.java") && analysis.criteria().stream().anyMatch(c->!Set.of("create","redirect").contains(c.capability()));
        if(request.role()==AgentRole.DIAGNOSIS) {
            var failure=store.decode(context.inputs().get("failure").content(),BuildEvidence.class);
            String logs=failure.stdout()+"\n"+failure.stderr();
            List<String> paths=input.task().impactedPaths().stream().filter(p->logs.contains(p.substring(p.lastIndexOf('/')+1))).toList();
            String strategy="UNSUPPORTED";
            String boot=input.baseline().get(BrownfieldSources.ROOT+"TargetApplication.java");
            if(failure.classification()==BuildEvidence.FailureClassification.COMPILATION && logs.contains("MissingApplication") && boot!=null && boot.contains("MissingApplication.class")) strategy="REPLACE_MISSING_BOOTSTRAP_TYPE";
            String redirect=input.baseline().get(GeneratedServiceSources.ROOT+"RedirectController.java");
            if(failure.classification()==BuildEvidence.FailureClassification.TEST && failure.failedTests().stream().anyMatch(t->t.endsWith("#createdCodeRedirectsToExactStoredTargetThroughHttp"))
                    && redirect!=null && redirect.contains("ResponseEntity.status(418)")) {
                strategy="RESTORE_REQUIRED_REDIRECT_STATUS"; paths=List.of(GeneratedServiceSources.ROOT+"RedirectController.java");
            }
            output=new EngineeringModels.Diagnosis(failure.classification(),paths,failure.failedTests(),logs.substring(Math.max(0,logs.length()-12000)),!strategy.equals("UNSUPPORTED"),strategy);
        } else if(request.role()==AgentRole.REPAIR) {
            var diagnosis=store.decode(context.inputs().get("diagnosis").content(),EngineeringModels.Diagnosis.class);
            String path, replacement;
            switch(diagnosis.strategy()) {
                case "REPLACE_MISSING_BOOTSTRAP_TYPE" -> {
                    path=BrownfieldSources.ROOT+"TargetApplication.java";
                    replacement=input.baseline().get(path).replace("MissingApplication.class","TargetApplication.class");
                }
                case "RESTORE_REQUIRED_REDIRECT_STATUS" -> {
                    path=GeneratedServiceSources.ROOT+"RedirectController.java";
                    replacement=input.baseline().get(path).replace("ResponseEntity.status(418)","ResponseEntity.status("+redirectStatus+")");
                }
                default -> throw new IllegalArgumentException("Evidence does not support an approved production repair");
            }
            String original=input.baseline().get(path);
            if(original.equals(replacement) || !input.task().impactedPaths().contains(path)) throw new IllegalArgumentException("Repair exceeds approved scope or changes nothing");
            output=new EngineeringModels.Proposal(List.of(new FileOperation(FileOperation.Operation.UPDATE,path,replacement,Hashes.sha256(original),
                    "Repair diagnosed "+diagnosis.strategy()+" using build evidence "+context.inputs().get("failure").sha256(),context.revisionId().toString(),input.task().criterionIds(),context.taskId(),context.inputHashes())));
        } else if(request.role()==AgentRole.IMPLEMENTATION || request.role()==AgentRole.TESTING) {
            Map<String,String> files=new LinkedHashMap<>();
            if(full) {
                String capability=input.task().key().replaceFirst("^(implement|test)-","");
                if(request.role()==AgentRole.IMPLEMENTATION) {
                    if(capability.equals("create")) { files.putAll(assets.buildFiles()); files.putAll(fullSources.production()); }
                    else { String path=FullShortenerSources.ROOT+"UrlCapabilities.java"; files.put(path,FullShortenerSources.enable(input.baseline().get(path),capability,redirectStatus)); }
                } else {
                    files.put(FullShortenerSources.testPath(capability),FullShortenerSources.test(capability,redirectStatus));
                    if(capability.equals("create")) { files.put(FullShortenerSources.TEST+"HttpSupport.java",FullShortenerSources.HTTP_SUPPORT); files.put("src/test/resources/application-test.yaml",FullShortenerSources.TEST_CONFIG); }
                }
            } else switch(input.task().key()) {
                case "implement-create" -> {
                    files.putAll(assets.buildFiles());
                    files.put(GeneratedServiceSources.ROOT+"ShortenerApplication.java",GeneratedServiceSources.APPLICATION);
                    files.put(GeneratedServiceSources.ROOT+"UrlStore.java",GeneratedServiceSources.STORE);
                    files.put(GeneratedServiceSources.ROOT+"UrlController.java",GeneratedServiceSources.CREATE);
                }
                case "implement-redirect" -> files.put(GeneratedServiceSources.ROOT+"RedirectController.java",GeneratedServiceSources.redirect(redirectStatus));
                case "test-create" -> files.put(GeneratedServiceSources.TEST+"CreateUrlTest.java",GeneratedServiceSources.CREATE_TEST);
                case "test-redirect" -> files.put(GeneratedServiceSources.TEST+"RedirectUrlTest.java",GeneratedServiceSources.redirectTest(redirectStatus));
                case "implement-analytics-total" -> { files.putAll(assets.buildFiles()); files.putAll(BrownfieldSources.total(input.baseline())); }
                case "implement-analytics-daily" -> files.putAll(BrownfieldSources.daily(input.baseline()));
                case "test-analytics-total" -> files.put(BrownfieldSources.TEST+"AnalyticsTotalTest.java",BrownfieldSources.analyticsTest(false));
                case "test-analytics-daily" -> files.put(BrownfieldSources.TEST+"AnalyticsDailyTest.java",BrownfieldSources.analyticsTest(true));
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
            boolean analytics=analysis.criteria().stream().anyMatch(c->c.capability().startsWith("analytics"));
            if(full) {
                analytics=false;
                decision=switch(request.role()) {
                    case ARCHITECTURE -> "PostgreSQL/Flyway stores links, hashed management secrets and shared rate buckets. Row locks serialize redirect counters and UTC daily aggregates. Approved capability changes enable controller/service runtime paths.";
                    case SECURITY_RISK -> "Validate all DNS answers at creation and redirect, reject private destinations and credentials, use SecureRandom codes and hashed management tokens, and enforce shared fixed-window quotas. Redirect destinations remain subject to browser DNS changes; no destination is fetched by the platform.";
                    case DOCUMENTATION -> "Set DB_URL, DB_USERNAME and DB_PASSWORD for PostgreSQL, then run the generated Maven Wrapper. POST /api/v1/urls; GET /{code}; GET or DELETE /api/v1/urls/{code}; GET /api/v1/urls/{code}/analytics. DELETE requires the creation response managementToken in X-Link-Token.";
                    default -> decision;
                };
            }
            if(analytics && request.role()==AgentRole.ARCHITECTURE) decision="Preserve the existing UrlController -> UrlService runtime. Successful redirects call recordRedirect; concurrent per-code counters expose total and UTC-day analytics through the existing controller. Baseline tests remain part of Maven verification.";
            if(analytics && request.role()==AgentRole.SECURITY_RISK) decision="Preserve existing URL behavior and inspect collision-safe codes and concurrent analytics counters. Existing scheme-prefix validation does not establish complete host/credential/URL security; production protections remain stage 5.";
            if(analytics && request.role()==AgentRole.DOCUMENTATION) decision="Run the generated service using the platform Maven Wrapper. POST /api/v1/urls creates a code; GET /{code} redirects and increments counters. GET /api/v1/urls/{code}/analytics returns totals and UTC daily counts; optional day=YYYY-MM-DD selects one UTC day. Generated HTTP tests exercise counts, independent codes, empty counts, unknown codes and query-day behavior; the original regression test remains.";
            if(input.task().key().equals("security-review")) {
                String production=input.baseline().entrySet().stream().filter(e->e.getKey().startsWith("src/main/java/")).map(Map.Entry::getValue).reduce("",String::concat);
                boolean daily=analysis.criteria().stream().anyMatch(c->c.capability().equals("analytics-daily"));
                for(String required:full ? List.of("SecureRandom","FOR UPDATE","MessageDigest.isEqual","getHost()","getUserInfo()","Retry-After","Clock.systemUTC") : analytics ? (daily ? List.of("SecureRandom","putIfAbsent","recordRedirect","totalClicks","dailyClicks","Clock.systemUTC") : List.of("SecureRandom","putIfAbsent","recordRedirect","totalClicks"))
                        : List.of("SecureRandom","putIfAbsent","getHost()","getUserInfo()","length()>2048","https")) {
                    if(!production.contains(required)) throw new IllegalArgumentException("Generated slice lacks required security control: "+required);
                }
                decision+=" Inspected current production source for the declared capability controls. Static token checks alone are not a complete security assessment.";
            }
            if(request.role()==AgentRole.DOCUMENTATION) {
                var build=context.inputs().get("build");
                var result=store.decode(build.content(),BuildEvidence.class);
                decision+=" Verified build artifact "+build.sha256()+" compiled "+result.compiledProductionPaths().size()+" production files and executed "+result.discoveredTests().size()+" tests; coverage report: "+result.coverage().reportLocation();
            }
            output=new EngineeringModels.Decision(decision,input.task().criterionIds(),context.inputHashes(),
                    full ? List.of("Generated tests substitute deterministic DNS and H2; PostgreSQL behavior is separately verified", "Fixed-window quotas use the socket peer; trusted proxy identity and external authentication remain deployment choices", "Release authorization does not deploy the generated service") : List.of("This requested minimal or legacy fixture uses in-memory storage", "Full PostgreSQL generation requires declaring the additional URL capabilities", "Release authorization does not deploy the generated service"));
        }
        return new ModelResponse("deterministic","engineering-v1",store.encode(output),Duration.ZERO);
    }
}
