package dev.ajaymatta.agentic.execution;

/** Fixed capabilities. There is deliberately no arbitrary-command capability. */
public interface EngineeringTool<I, O> {
    Capability capability();
    O execute(RepositoryWorkspace workspace, I input);

    enum Capability { READ_REPOSITORY, APPLY_PROPOSAL, MAVEN_CLEAN_VERIFY, RESTORE_BASELINE }
}
