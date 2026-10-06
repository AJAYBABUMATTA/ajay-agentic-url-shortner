package dev.ajaymatta.agentic.engineering;

import dev.ajaymatta.agentic.execution.*;
import dev.ajaymatta.agentic.intelligence.IntelligenceStore;
import java.util.*;

public class EngineeringAgent implements Agent {
    private final AgentRole role;
    private final DeterministicEngineeringProvider provider;
    private final IntelligenceStore store;
    public EngineeringAgent(AgentRole role, DeterministicEngineeringProvider provider, IntelligenceStore store) { this.role=role; this.provider=provider; this.store=store; }
    @Override public AgentRole role() { return role; }
    @Override public AgentOutput execute(ExecutionContext context) {
        var response=provider.generate(new ModelProvider.ModelRequest(role,"1.0",context));
        var type=switch(role) {
            case IMPLEMENTATION, TESTING -> EngineeringArtifact.ArtifactType.FILE_PROPOSAL;
            case ARCHITECTURE -> EngineeringArtifact.ArtifactType.ARCHITECTURE;
            case SECURITY_RISK -> EngineeringArtifact.ArtifactType.SECURITY_REVIEW;
            case DOCUMENTATION -> EngineeringArtifact.ArtifactType.DOCUMENTATION;
            default -> throw new IllegalArgumentException("Unsupported role");
        };
        var artifact=new EngineeringArtifact(UUID.randomUUID(),context.revisionId(),context.taskId(),type,"engineering/1.0",
                response.content(),Hashes.sha256(response.content()),context.inputHashes(),store.now());
        List<FileOperation> proposals=type==EngineeringArtifact.ArtifactType.FILE_PROPOSAL
                ? store.decode(response.content(),EngineeringModels.Proposal.class).operations() : List.of();
        return new AgentOutput(List.of(artifact),proposals);
    }
}
