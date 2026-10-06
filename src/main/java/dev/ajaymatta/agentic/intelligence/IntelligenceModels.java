package dev.ajaymatta.agentic.intelligence;

import dev.ajaymatta.agentic.planning.EngineeringPlan;
import dev.ajaymatta.agentic.repository.RepositoryMap;
import dev.ajaymatta.agentic.repository.RepositorySnapshot;
import dev.ajaymatta.agentic.execution.ExecutionAttempt;
import dev.ajaymatta.agentic.execution.EngineeringArtifact;
import java.util.UUID;
import java.util.List;
import java.util.Map;

public final class IntelligenceModels {
    private IntelligenceModels() {}
    public record AmbiguityDecision(boolean resolved, List<RequirementAnalysis.Question> questions, boolean sourceMutationAllowed) {
        public AmbiguityDecision { questions = List.copyOf(questions); }
    }
    public record SnapshotInput(String manifestHash, List<RepositorySnapshot.FileEntry> files, Map<String, String> contents) {
        public SnapshotInput { files = List.copyOf(files); contents = Map.copyOf(contents); }
    }
    public record View(RequirementAnalysis requirement, RepositoryMap repository, EngineeringPlan plan,
                       String planHash, List<String> invalidatedArtifactIds, List<String> reusedArtifactIds,
                       List<ArtifactSummary> artifacts, List<ExecutionAttempt> attempts) {
        public View {
            invalidatedArtifactIds = List.copyOf(invalidatedArtifactIds); reusedArtifactIds = List.copyOf(reusedArtifactIds);
            artifacts = List.copyOf(artifacts); attempts = List.copyOf(attempts);
        }
    }
    public record ArtifactSummary(UUID id, UUID taskId, EngineeringArtifact.ArtifactType type, String sha256, List<String> inputHashes) {
        public ArtifactSummary { inputHashes = List.copyOf(inputHashes); }
    }
}
