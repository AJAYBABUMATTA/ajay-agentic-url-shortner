ALTER TABLE engineering_runs ADD COLUMN stop_requested BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE engineering_runs ADD COLUMN cancel_requested BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE engineering_runs ADD COLUMN stop_reason TEXT;
CREATE TABLE execution_gates (
    id UUID PRIMARY KEY,
    revision_id UUID NOT NULL REFERENCES workflow_revisions(id),
    task_id UUID,
    gate VARCHAR(100) NOT NULL,
    passed BOOLEAN NOT NULL,
    evidence_hash VARCHAR(64) NOT NULL,
    summary TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    FOREIGN KEY (task_id,revision_id) REFERENCES agent_tasks(id,revision_id)
);
