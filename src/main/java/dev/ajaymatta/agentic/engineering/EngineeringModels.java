package dev.ajaymatta.agentic.engineering;

import dev.ajaymatta.agentic.execution.*;
import dev.ajaymatta.agentic.planning.EngineeringPlan;
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
                               List<String> limitations) {}
    public record View(String state, String decision, RepositoryWorkspace workspace,
                       List<EngineeringArtifact> artifacts, SliceOutcome outcome) {}
}
