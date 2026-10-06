package dev.ajaymatta.agentic.intelligence;

import dev.ajaymatta.agentic.execution.*;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/** Four specialized analysis agents share a schema-bound dispatch adapter. */
public final class IntelligenceAgent implements Agent {
    private final AgentRole role;
    private final EngineeringArtifact.ArtifactType artifactType;
    private final ModelProvider provider;
    private final Clock clock;
    public IntelligenceAgent(AgentRole role, EngineeringArtifact.ArtifactType artifactType, ModelProvider provider, Clock clock) {
        this.role = role; this.artifactType = artifactType; this.provider = provider; this.clock = clock;
    }
    @Override public AgentRole role() { return role; }
    @Override public AgentOutput execute(ExecutionContext context) {
        var response = provider.generate(new ModelProvider.ModelRequest(role, "1.0", context));
        var artifact = new EngineeringArtifact(UUID.randomUUID(), context.revisionId(), context.taskId(), artifactType,
                "1.0", response.content(), Hashes.sha256(response.content()), context.inputHashes(),
                clock.instant().truncatedTo(ChronoUnit.MICROS));
        return new AgentOutput(List.of(artifact), List.of());
    }
}
