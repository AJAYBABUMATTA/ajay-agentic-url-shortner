package dev.ajaymatta.agentic.workflow.domain;

/** Declarative gate vocabulary; enforcement belongs to the executor, never API callers. */
public enum Gate {
    REQUIREMENT_RESOLVED, DEPENDENCIES_SUCCEEDED, CURRENT_INPUT_HASHES,
    CHANGE_APPROVED, PROPOSAL_VALID, POLICY_PASSED, PATCH_APPLIED,
    ARTIFACT_VALIDATED, BUILD_AND_TESTS_PASSED, TRACEABILITY_COMPLETE, RELEASE_APPROVED
}
