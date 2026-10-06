CREATE TABLE worker_leases (
    revision_id UUID PRIMARY KEY REFERENCES workflow_revisions(id),
    workflow_id UUID NOT NULL REFERENCES workflows(id),
    owner_id VARCHAR(128) NOT NULL,
    token UUID NOT NULL,
    phase VARCHAR(32) NOT NULL,
    heartbeat_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX expired_worker_leases ON worker_leases(closed_at,expires_at);
