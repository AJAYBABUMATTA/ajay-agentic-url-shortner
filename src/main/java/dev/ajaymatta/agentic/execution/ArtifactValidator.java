package dev.ajaymatta.agentic.execution;

public interface ArtifactValidator {
    ValidationResult validate(EngineeringArtifact artifact, ExecutionContext context);
}
