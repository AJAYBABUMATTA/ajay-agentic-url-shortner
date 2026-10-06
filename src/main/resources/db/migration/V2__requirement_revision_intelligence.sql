CREATE TABLE clarification_records (
    id UUID PRIMARY KEY,
    workflow_id UUID NOT NULL,
    source_revision_id UUID NOT NULL,
    revision_id UUID NOT NULL UNIQUE,
    actor_id VARCHAR(160) NOT NULL,
    answers TEXT NOT NULL,
    reason TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY (source_revision_id, workflow_id) REFERENCES workflow_revisions(id, workflow_id),
    FOREIGN KEY (revision_id, workflow_id) REFERENCES workflow_revisions(id, workflow_id)
);

CREATE TABLE artifact_reuse (
    id UUID PRIMARY KEY,
    revision_id UUID NOT NULL,
    source_artifact_id UUID NOT NULL REFERENCES engineering_artifacts(id),
    reused_artifact_id UUID NOT NULL,
    verified_repository_hash VARCHAR(64) NOT NULL CHECK (CHAR_LENGTH(verified_repository_hash) = 64),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY (reused_artifact_id, revision_id) REFERENCES engineering_artifacts(id, revision_id)
);
