CREATE TABLE workflows (
    id UUID PRIMARY KEY,
    current_revision INTEGER NOT NULL CHECK (current_revision > 0),
    status VARCHAR(40) NOT NULL CHECK (status IN ('RECEIVED','INTERPRETING','AWAITING_CLARIFICATION','PLANNING',
        'AWAITING_CHANGE_APPROVAL','EXECUTING','VALIDATING','AWAITING_RELEASE_APPROVAL','RELEASE_READY',
        'SAFE_STOPPED','ROLLED_BACK','FAILED','CANCELLED')),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK (updated_at >= created_at)
);

CREATE TABLE workflow_revisions (
    id UUID PRIMARY KEY,
    workflow_id UUID NOT NULL REFERENCES workflows(id),
    revision_number INTEGER NOT NULL CHECK (revision_number > 0),
    parent_revision_id UUID,
    requirement TEXT NOT NULL CHECK (CHAR_LENGTH(requirement) BETWEEN 1 AND 10000),
    requirement_hash VARCHAR(64) NOT NULL CHECK (CHAR_LENGTH(requirement_hash) = 64),
    repository_path VARCHAR(512) NOT NULL,
    status VARCHAR(40) NOT NULL CHECK (status IN ('RECEIVED','INTERPRETING','AWAITING_CLARIFICATION','PLANNING',
        'AWAITING_CHANGE_APPROVAL','EXECUTING','VALIDATING','AWAITING_RELEASE_APPROVAL','RELEASE_READY',
        'SAFE_STOPPED','ROLLED_BACK','FAILED','CANCELLED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (workflow_id, revision_number),
    UNIQUE (id, workflow_id),
    FOREIGN KEY (parent_revision_id, workflow_id) REFERENCES workflow_revisions(id, workflow_id),
    CHECK (parent_revision_id IS NULL OR parent_revision_id <> id),
    CHECK ((revision_number = 1 AND parent_revision_id IS NULL) OR (revision_number > 1 AND parent_revision_id IS NOT NULL))
);

CREATE TABLE agent_tasks (
    id UUID PRIMARY KEY,
    revision_id UUID NOT NULL REFERENCES workflow_revisions(id),
    task_key VARCHAR(160) NOT NULL,
    agent_role VARCHAR(40) NOT NULL CHECK (agent_role IN ('REQUIREMENT_INTERPRETATION','AMBIGUITY_ANALYSIS',
        'REPOSITORY_ANALYSIS','PLANNING','ARCHITECTURE','IMPLEMENTATION','TESTING','DIAGNOSIS','REPAIR',
        'DOCUMENTATION','SECURITY_RISK','RELEASE_READINESS')),
    state VARCHAR(30) NOT NULL CHECK (state IN ('PENDING','READY','RUNNING','AWAITING_APPROVAL',
        'SUCCEEDED','FAILED','CANCELLED','INVALIDATED')),
    entry_gates TEXT NOT NULL,
    exit_gates TEXT NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    max_attempts INTEGER NOT NULL CHECK (max_attempts > 0),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (revision_id, task_key),
    UNIQUE (id, revision_id),
    CHECK (attempt_count <= max_attempts),
    CHECK (updated_at >= created_at)
);

CREATE TABLE task_dependencies (
    revision_id UUID NOT NULL,
    task_id UUID NOT NULL,
    depends_on_task_id UUID NOT NULL,
    PRIMARY KEY (task_id, depends_on_task_id),
    FOREIGN KEY (task_id, revision_id) REFERENCES agent_tasks(id, revision_id),
    FOREIGN KEY (depends_on_task_id, revision_id) REFERENCES agent_tasks(id, revision_id),
    CHECK (task_id <> depends_on_task_id)
);

CREATE TABLE execution_attempts (
    id UUID PRIMARY KEY,
    revision_id UUID NOT NULL,
    task_id UUID NOT NULL,
    attempt_number INTEGER NOT NULL CHECK (attempt_number > 0),
    executor VARCHAR(80) NOT NULL,
    state VARCHAR(20) NOT NULL CHECK (state IN ('RUNNING','SUCCEEDED','FAILED','TIMED_OUT','CANCELLED')),
    input_hashes TEXT NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    evidence_location TEXT,
    UNIQUE (task_id, attempt_number),
    UNIQUE (id, revision_id),
    FOREIGN KEY (task_id, revision_id) REFERENCES agent_tasks(id, revision_id),
    CHECK ((state = 'RUNNING' AND completed_at IS NULL) OR
        (state <> 'RUNNING' AND completed_at >= started_at AND evidence_location IS NOT NULL AND completed_at IS NOT NULL))
);

CREATE TABLE engineering_artifacts (
    id UUID PRIMARY KEY,
    revision_id UUID NOT NULL,
    task_id UUID NOT NULL,
    artifact_type VARCHAR(40) NOT NULL CHECK (artifact_type IN ('REQUIREMENT_ANALYSIS','AMBIGUITY_ANALYSIS',
        'REPOSITORY_MAP','TASK_PLAN','ARCHITECTURE','FILE_PROPOSAL','MANIFEST','UNIFIED_DIFF','BUILD_EVIDENCE',
        'DIAGNOSIS','DOCUMENTATION','SECURITY_REVIEW','ENGINEERING_OUTCOME')),
    schema_version VARCHAR(30) NOT NULL,
    content TEXT NOT NULL,
    sha256 VARCHAR(64) NOT NULL CHECK (CHAR_LENGTH(sha256) = 64),
    input_hashes TEXT NOT NULL,
    producing_agent VARCHAR(80) NOT NULL,
    provider VARCHAR(80) NOT NULL,
    model VARCHAR(120) NOT NULL,
    invalidated_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE (id, revision_id),
    UNIQUE (id, revision_id, sha256),
    FOREIGN KEY (task_id, revision_id) REFERENCES agent_tasks(id, revision_id)
);

CREATE TABLE validation_results (
    id UUID PRIMARY KEY,
    revision_id UUID NOT NULL,
    artifact_id UUID NOT NULL,
    artifact_hash VARCHAR(64) NOT NULL,
    validator VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PASSED','FAILED','INCONCLUSIVE')),
    summary TEXT NOT NULL,
    evidence_location TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY (artifact_id, revision_id, artifact_hash) REFERENCES engineering_artifacts(id, revision_id, sha256)
);

CREATE TABLE build_evidence (
    attempt_id UUID PRIMARY KEY REFERENCES execution_attempts(id),
    capability VARCHAR(40) NOT NULL CHECK (capability = 'MAVEN_CLEAN_VERIFY'),
    exit_code INTEGER NOT NULL,
    duration_ms BIGINT NOT NULL CHECK (duration_ms >= 0),
    timed_out BOOLEAN NOT NULL,
    stdout TEXT NOT NULL CHECK (CHAR_LENGTH(stdout) <= 65536),
    stderr TEXT NOT NULL CHECK (CHAR_LENGTH(stderr) <= 65536),
    output_truncated BOOLEAN NOT NULL,
    compiled_production_paths TEXT NOT NULL,
    discovered_tests TEXT NOT NULL,
    failed_tests TEXT NOT NULL,
    coverage_available BOOLEAN NOT NULL,
    covered_lines BIGINT NOT NULL CHECK (covered_lines >= 0),
    missed_lines BIGINT NOT NULL CHECK (missed_lines >= 0),
    coverage_report_location TEXT,
    failure_classification VARCHAR(30) NOT NULL CHECK (failure_classification IN
        ('NONE','COMPILATION','TEST','POLICY','DEPENDENCY','INFRASTRUCTURE','TIMEOUT','UNKNOWN')),
    CHECK (failure_classification <> 'NONE' OR (exit_code = 0 AND timed_out = FALSE)),
    CHECK ((coverage_available = TRUE AND coverage_report_location IS NOT NULL) OR
        (coverage_available = FALSE AND covered_lines = 0 AND missed_lines = 0))
);

CREATE TABLE repository_workspaces (
    id UUID PRIMARY KEY,
    revision_id UUID NOT NULL UNIQUE REFERENCES workflow_revisions(id),
    repository_location TEXT NOT NULL,
    baseline_location TEXT NOT NULL,
    baseline_manifest_hash VARCHAR(64) NOT NULL CHECK (CHAR_LENGTH(baseline_manifest_hash) = 64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK (repository_location <> baseline_location)
);

CREATE TABLE policy_decisions (
    id UUID PRIMARY KEY,
    revision_id UUID NOT NULL REFERENCES workflow_revisions(id),
    policy VARCHAR(100) NOT NULL,
    policy_version VARCHAR(30) NOT NULL,
    subject_hash VARCHAR(64) NOT NULL CHECK (CHAR_LENGTH(subject_hash) = 64),
    verdict VARCHAR(20) NOT NULL CHECK (verdict IN ('ALLOW','DENY','REQUIRE_HUMAN')),
    reason TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE approvals (
    id UUID PRIMARY KEY,
    revision_id UUID NOT NULL,
    artifact_id UUID NOT NULL,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('CHANGE','RELEASE')),
    evidence_hash VARCHAR(64) NOT NULL,
    actor_id VARCHAR(160) NOT NULL,
    decision VARCHAR(20) NOT NULL CHECK (decision IN ('APPROVED','REJECTED')),
    reason TEXT NOT NULL,
    invalidated_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY (artifact_id, revision_id, evidence_hash) REFERENCES engineering_artifacts(id, revision_id, sha256)
);

CREATE TABLE recovery_decisions (
    id UUID PRIMARY KEY,
    revision_id UUID NOT NULL,
    attempt_id UUID NOT NULL,
    action VARCHAR(30) NOT NULL CHECK (action IN ('REPAIR','RETRY','RESTORE_BASELINE','HUMAN_INTERVENTION','SAFE_STOP','TERMINAL_FAILURE')),
    maximum_attempts INTEGER NOT NULL CHECK (maximum_attempts > 0),
    delay_ms BIGINT NOT NULL CHECK (delay_ms >= 0),
    reason TEXT NOT NULL,
    repair_artifact_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY (attempt_id, revision_id) REFERENCES execution_attempts(id, revision_id),
    FOREIGN KEY (repair_artifact_id, revision_id) REFERENCES engineering_artifacts(id, revision_id)
);

CREATE TABLE rollback_actions (
    id UUID PRIMARY KEY,
    revision_id UUID NOT NULL REFERENCES workflow_revisions(id),
    reason TEXT NOT NULL,
    expected_manifest_hash VARCHAR(64) NOT NULL CHECK (CHAR_LENGTH(expected_manifest_hash) = 64),
    restored_manifest_hash VARCHAR(64),
    verified BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CHECK (restored_manifest_hash IS NULL OR CHAR_LENGTH(restored_manifest_hash) = 64),
    CHECK (verified = FALSE OR (restored_manifest_hash IS NOT NULL AND expected_manifest_hash = restored_manifest_hash))
);

CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    workflow_id UUID NOT NULL,
    revision_id UUID NOT NULL,
    task_id UUID,
    event_type VARCHAR(100) NOT NULL,
    actor_id VARCHAR(160) NOT NULL,
    details TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY (revision_id, workflow_id) REFERENCES workflow_revisions(id, workflow_id),
    FOREIGN KEY (task_id, revision_id) REFERENCES agent_tasks(id, revision_id)
);

CREATE INDEX idx_workflow_status ON workflows(status, updated_at);
CREATE INDEX idx_task_dispatch ON agent_tasks(state, revision_id);
CREATE INDEX idx_dependency_reverse ON task_dependencies(depends_on_task_id);
CREATE INDEX idx_artifact_revision ON engineering_artifacts(revision_id, artifact_type);
CREATE INDEX idx_audit_lineage ON audit_events(workflow_id, created_at, id);
