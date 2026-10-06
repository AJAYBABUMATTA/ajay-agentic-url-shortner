# Observability

Actuator provides database-aware readiness, database-independent liveness and
baseline Prometheus JVM/process/HTTP/pool instrumentation. Workflow success rate,
retry/rollback frequency, MTTR and end-to-end outcome latency remain pending.
Analysis success is not engineering success and must not enter release metrics.

GET workflow returns current requirement/repository/plan evidence, artifact IDs
and hashes, input lineage, attempts and audit. Events cover agent start/validation,
snapshot verification, state transitions, revision creation and safe stop. Hashes
and IDs belong in the ledger rather than unbounded metric labels.

Revision actions record authenticated operator labels. Shared-token operator labels
are not independently authenticated enterprise identities. Requirement text and
source inventory are persisted for engineering reasoning; public endpoints require
production authentication before deployment. Error responses omit paths/SQL/parser
internals. Tokens are neither persisted nor passed to intelligence providers.

Audit rows have an append-only application surface, not tamper-proof database-owner
protection. Baseline copies and source are content-verified. Real engineering builds
persist exit code, duration, timeout, compiled production paths, discovered/failed
tests, coverage and failure classification. Each stream retains at most 60,000 bytes
while continuing to drain the child pipe; the database cap is 65,536 characters.
GET /api/v1/workflows/{id}/engineering exposes full proposal content, diff, manifest,
architecture/security/docs artifacts and the bounded slice outcome. Child processes
do not inherit DB_PASSWORD, operator tokens, JAVA_TOOL_OPTIONS or MAVEN_OPTS.
