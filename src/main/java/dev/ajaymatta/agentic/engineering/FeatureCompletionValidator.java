package dev.ajaymatta.agentic.engineering;

import dev.ajaymatta.agentic.execution.*;
import dev.ajaymatta.agentic.intelligence.*;
import dev.ajaymatta.agentic.governance.*;
import dev.ajaymatta.agentic.repository.RepositoryTools;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class FeatureCompletionValidator {
    private final EngineeringStore store;
    private final IntelligenceStore evidence;
    private final ProposalTool files;
    private final RepositoryTools repositories;
    public FeatureCompletionValidator(EngineeringStore store,IntelligenceStore evidence,ProposalTool files,RepositoryTools repositories) {
        this.store=store; this.evidence=evidence; this.files=files; this.repositories=repositories;
    }
    public EngineeringModels.SliceOutcome inspect(UUID revision,boolean released,String approvedOutcomeHash) {
        var view=evidence.view(revision); var analysis=view.requirement(); var workspace=store.workspace(revision);
        var artifacts=evidence.artifacts(revision); var buildArtifact=store.latest(revision,EngineeringArtifact.ArtifactType.BUILD_EVIDENCE);
        var build=evidence.decode(buildArtifact.content(),BuildEvidence.class); var patch=store.latest(revision,EngineeringArtifact.ArtifactType.MANIFEST);
        var manifest=evidence.decode(patch.content(),EngineeringModels.AppliedPatch.class);
        var current=files.read(workspace); var operations=artifacts.stream().filter(a->a.type()==EngineeringArtifact.ArtifactType.FILE_PROPOSAL)
                .flatMap(a->evidence.decode(a.content(),EngineeringModels.Proposal.class).operations().stream()).toList();
        List<EngineeringModels.CriterionEvidence> trace=new ArrayList<>(); boolean production=true,tests=true,connected=true;
        Set<String> expectedCases=new HashSet<>();
        boolean full=current.containsKey(FullShortenerSources.ROOT+"UrlCapabilities.java");
        for(var criterion:analysis.criteria()) {
            var productionPaths=operations.stream().filter(o->o.criterionIds().contains(criterion.id()) && o.path().startsWith("src/main/java/")).map(FileOperation::path).distinct().sorted().toList();
            var testPaths=operations.stream().filter(o->o.criterionIds().contains(criterion.id()) && o.path().startsWith("src/test/java/")).map(FileOperation::path).distinct().sorted().toList();
            var executed=testPaths.stream().flatMap(path->build.discoveredTests().stream().filter(name->name.startsWith(path.substring("src/test/java/".length()).replace('/','.').replace(".java","")+"#"))).distinct().sorted().toList();
            production &= !productionPaths.isEmpty(); tests &= !testPaths.isEmpty() && !executed.isEmpty();
            String expectedTest=full ? FullShortenerSources.test(criterion.capability(),criterion.description().contains("301") ? 301 : 302) : switch(criterion.capability()) {
                case "create" -> GeneratedServiceSources.CREATE_TEST;
                case "redirect" -> GeneratedServiceSources.redirectTest(criterion.description().contains("301") ? 301 : 302);
                case "analytics-total" -> BrownfieldSources.analyticsTest(false);
                case "analytics-daily" -> BrownfieldSources.analyticsTest(true);
                default -> null;
            };
            tests &= expectedTest!=null && testPaths.stream().anyMatch(path->expectedTest.equals(current.get(path)));
            String testClass=full ? "dev.ajaymatta.generated.shortener."+FullShortenerSources.className(criterion.capability()) : switch(criterion.capability()) {
                case "create" -> "dev.ajaymatta.generated.CreateUrlTest";
                case "redirect" -> "dev.ajaymatta.generated.RedirectUrlTest";
                case "analytics-total" -> "dev.ajaymatta.target.AnalyticsTotalTest";
                case "analytics-daily" -> "dev.ajaymatta.target.AnalyticsDailyTest";
                default -> "unsupported";
            };
            List<String> names=criterion.capability().startsWith("analytics") ? List.of("redirectsIncrementRealAnalytics","newCodeHasNoVisitsAndOtherCodesStayIndependent","missingCodeHasNoAnalytics")
                    : criterion.capability().equals("create") ? List.of("validTargetCreatesUniqueUsableCodes","rejectsUnsupportedSchemeMissingHostAndCredentials","rejectsMissingTarget")
                    : List.of("createdCodeRedirectsToExactStoredTargetThroughHttp","unknownCodeReturns404WithoutLocation");
            if(full) names=List.of(FullShortenerSources.caseName(criterion.capability()));
            names.forEach(name->expectedCases.add(testClass+"#"+name));
            connected &= full ? current.get(FullShortenerSources.ROOT+"UrlCapabilities.java").contains("\""+criterion.capability()+"\"") && current.getOrDefault(FullShortenerSources.ROOT+"UrlController.java","").contains("UrlCapabilities.enabled(capability)") : switch(criterion.capability()) {
                case "create" -> current.getOrDefault(GeneratedServiceSources.ROOT+"UrlController.java","").contains("store.create(request.target())");
                case "redirect" -> current.getOrDefault(GeneratedServiceSources.ROOT+"RedirectController.java","").contains("store.resolve(code)");
                case "analytics-total" -> current.getOrDefault(BrownfieldSources.ROOT+"UrlController.java","").contains("service.recordRedirect(code)") && current.getOrDefault(BrownfieldSources.ROOT+"UrlController.java","").contains("service.totalClicks(code)");
                case "analytics-daily" -> current.getOrDefault(BrownfieldSources.ROOT+"UrlController.java","").contains("service.dailyClicks(code)") && current.getOrDefault(BrownfieldSources.ROOT+"UrlService.java","").contains("Clock.systemUTC()");
                default -> false;
            };
            trace.add(new EngineeringModels.CriterionEvidence(criterion.id(),productionPaths,testPaths,executed));
        }
        var productionChanges=operations.stream().filter(o->o.type()!=FileOperation.Operation.DELETE && o.path().startsWith("src/main/java/")).map(FileOperation::path).distinct().toList();
        var policies=store.policies(revision); var approvals=store.approvals(revision);
        var gates=new ArrayList<EngineeringModels.GateCheck>();
        check(gates,"1_CRITERIA_TO_PRODUCTION",production,patch.sha256(),"Every behavioral criterion maps to actual production operations");
        check(gates,"2_MEANINGFUL_GENERATED_TESTS",tests,patch.sha256(),"Criterion tests match the requirement-specific HTTP test capability; test weakening cannot pass");
        check(gates,"3_GENERATED_PRODUCTION_COMPILED",!productionChanges.isEmpty() && build.compiledProductionPaths().containsAll(productionChanges),buildArtifact.sha256(),"Compiler input/class output evidence covers changed production paths");
        boolean executed=build.discoveredTests().containsAll(expectedCases);
        var snapshot=evidence.artifact(revision,EngineeringArtifact.ArtifactType.MANIFEST).orElseThrow();
        var baseline=evidence.decode(snapshot.content(),IntelligenceModels.SnapshotInput.class);
        for(String path:baseline.contents().keySet()) if(path.startsWith("src/test/java/") && path.endsWith(".java")) {
            String prefix=path.substring("src/test/java/".length()).replace('/','.').replace(".java","")+"#";
            executed &= build.discoveredTests().stream().anyMatch(name->name.startsWith(prefix));
        }
        check(gates,"4_GENERATED_AND_BASELINE_TESTS_EXECUTED",executed,buildArtifact.sha256(),"Required generated HTTP cases and existing regression classes were discovered and executed");
        check(gates,"5_CONNECTED_RUNTIME_PATH",connected && executed,buildArtifact.sha256(),"HTTP cases exercise create/redirect or controller/service analytics paths");
        check(gates,"6_BUILD_AND_TESTS_PASS",build.exitCode()==0 && !build.timedOut() && build.failedTests().isEmpty() && build.classification()==BuildEvidence.FailureClassification.NONE && build.coverage().available(),buildArtifact.sha256(),"Real fixed clean verify succeeded with test and coverage evidence");
        boolean policy=policies.stream().anyMatch(p->p.policy().equals("controlled-engineering-proposal") && p.subjectHash().equals(patch.sha256()) && p.verdict()==PolicyDecision.Verdict.ALLOW)
                && artifacts.stream().anyMatch(a->a.type()==EngineeringArtifact.ArtifactType.SECURITY_REVIEW && store.validated(a)
                    && policies.stream().anyMatch(p->p.subjectHash().equals(a.sha256()) && p.verdict()==PolicyDecision.Verdict.ALLOW)
                    && a.taskId().equals(UUID.nameUUIDFromBytes((revision+":security-review").getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        check(gates,"7_REQUIRED_POLICIES_PASS",policy && !store.stopped(revision),patch.sha256(),"Exact current patch policy and post-build security review exist; no governed stop is active");
        boolean hashes=ProposalTool.manifest(current).equals(manifest.afterHash()) && artifacts.stream().allMatch(a->a.revisionId().equals(revision) && Hashes.sha256(a.content()).equals(a.sha256()));
        check(gates,"8_CURRENT_REVISION_ARTIFACT_HASHES",hashes,patch.sha256(),"Current files match the last applied manifest and all retained current-revision artifact hashes match");
        String selector=baselineSelector(revision);
        boolean upstream=repositories.currentManifest(selector).equals(workspace.baselineManifestHash());
        check(gates,"9_EXACT_CURRENT_HUMAN_CHANGE_APPROVAL",upstream && store.approved(revision,view.planHash()),view.planHash(),"Exact current plan is approved and upstream repository matches its approved snapshot");
        boolean human=released && approvedOutcomeHash!=null && approvals.stream().anyMatch(a->a.kind()==Approval.Kind.RELEASE && a.decision()==Approval.Decision.APPROVED && a.evidenceHash().equals(approvedOutcomeHash));
        check(gates,"10_EXACT_OUTCOME_RELEASE_APPROVAL",human,approvedOutcomeHash==null ? view.planHash() : approvedOutcomeHash,"Release requires authenticated approval of immutable current-revision outcome evidence");
        boolean complete=gates.subList(0,9).stream().allMatch(EngineeringModels.GateCheck::passed);
        boolean ready=complete && human;
        return new EngineeringModels.SliceOutcome(ready,ready ? "RELEASE_READY" : complete ? "AWAITING_EXACT_OUTCOME_APPROVAL" : "FEATURE_GATE_FAILED",view.planHash(),manifest.afterHash(),build,trace,
                artifacts.stream().filter(a->a.type()!=EngineeringArtifact.ArtifactType.ENGINEERING_OUTCOME).map(EngineeringArtifact::sha256).toList(),
                List.of(full ? "PostgreSQL target; deterministic generated tests use H2/DNS fixtures; separate PostgreSQL HTTP tests verify transactions and concurrency" : "Requested minimal/legacy target is in memory; full PostgreSQL generation is available for additional declared URL capabilities", "Readiness covers declared criteria and configured policies; approval does not deploy code"),
                complete,gates,approvals,policies,evidence.view(revision).attempts(),store.recovery(revision),analysis.assumptions(),analysis.risks());
    }
    private String baselineSelector(UUID revision) {
        // The workflow revision owns the selector; no caller/model-selected build path is used.
        return evidence.repositorySelector(revision);
    }
    private static void check(List<EngineeringModels.GateCheck> gates,String name,boolean passed,String hash,String reason) { gates.add(new EngineeringModels.GateCheck(name,passed,hash,reason)); }
}
