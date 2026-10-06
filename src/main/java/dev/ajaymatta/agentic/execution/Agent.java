package dev.ajaymatta.agentic.execution;

import java.util.List;

public interface Agent {
    AgentRole role();
    AgentOutput execute(ExecutionContext context);

    record AgentOutput(List<EngineeringArtifact> artifacts, List<FileOperation> proposals) {
        public AgentOutput {
            artifacts = List.copyOf(artifacts);
            proposals = List.copyOf(proposals);
            if (artifacts.isEmpty()) throw new IllegalArgumentException("Agent must produce an artifact");
        }
    }
}
