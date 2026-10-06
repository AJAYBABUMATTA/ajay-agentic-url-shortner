CREATE TABLE engineering_runs (
    revision_id UUID PRIMARY KEY REFERENCES workflow_revisions(id),
    state VARCHAR(20) NOT NULL CHECK (state IN ('QUEUED','RUNNING','SUCCEEDED','FAILED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP WITH TIME ZONE,
    decision TEXT
);
