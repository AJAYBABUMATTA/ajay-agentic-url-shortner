package dev.ajaymatta.agentic.engineering;

import dev.ajaymatta.agentic.execution.*;
import dev.ajaymatta.agentic.planning.EngineeringPlan;
import dev.ajaymatta.agentic.governance.*;
import java.time.Instant;
import java.util.*;

public final class EngineeringModels {
    private EngineeringModels() {}
    public record TaskInput(EngineeringPlan.PlannedTask task, Map<String,String> baseline) {
        public TaskInput { baseline = Map.copyOf(baseline); }
    }
    public record Proposal(List<FileOperation> operations) {
        public Proposal { operations = List.copyOf(operations); }
    }
    public record Decision(String decision, List<String> criterionIds, List<String> evidenceHashes, List<String> limitations) {}
    public record AppliedPatch(String beforeHash, String afterHash, Map<String,String> files,
                               String unifiedDiff, List<FileOperation> operations) {}
    public record CriterionEvidence(String criterionId, List<String> productionPaths, List<String> testPaths,
                                    List<String> executedTests) {}
    public record SliceOutcome(boolean releaseReady, String decision, String planHash, String manifestHash,
                               BuildEvidence build, List<CriterionEvidence> traceability, List<String> artifactHashes,
                               List<String> limitations, boolean featureComplete, List<GateCheck> gates,
                               List<Approval> approvals,List<PolicyDecision> policies,List<ExecutionAttempt> attempts,
                               List<RecoveryEvidence> recovery,List<String> assumptions,List<String> risks) {
        public SliceOutcome(boolean releaseReady,String decision,String planHash,String manifestHash,BuildEvidence build,
                            List<CriterionEvidence> traceability,List<String> artifactHashes,List<String> limitations) {
            this(releaseReady,decision,planHash,manifestHash,build,traceability,artifactHashes,limitations,false,
                    List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of());
        }
    }
    public record View(String state, String decision, RepositoryWorkspace workspace,
                       List<EngineeringArtifact> artifacts, SliceOutcome outcome,List<Approval> approvals,
                       List<PolicyDecision> policies,List<RecoveryEvidence> recovery,List<RollbackAction> rollbacks,List<GateCheck> gates) {}
    public record GateCheck(String gate,boolean passed,String evidenceHash,String summary) {}
    public record Diagnosis(BuildEvidence.FailureClassification classification,List<String> affectedPaths,
                            List<String> failedTests,String evidenceExcerpt,boolean repairable,String strategy) {}
    public record RecoveryEvidence(UUID id,UUID attemptId,String action,int maximumAttempts,long delayMs,
                                   String reason,UUID repairArtifactId,Instant createdAt) {}
}
