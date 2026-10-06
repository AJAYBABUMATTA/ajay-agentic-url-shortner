# Deployment status

This is a local assessment prototype with runnable requirement intelligence and
planning. Compose currently supplies PostgreSQL 16 on localhost with a required
password and durable named volume. The application runs through Maven. No final
application/build image or two-orchestrator configuration exists yet.

Configure DB_URL, DB_USERNAME, DB_PASSWORD and AGENTIC_OPERATOR_TOKEN explicitly.
The operator token has no insecure default. Use distinct approved repository and
workspace roots with restrictive access; workspace/source inventory can contain
private engineering material. Do not commit secrets or runtime workspaces.

The intake worker claims rows atomically. It is not a distributed crash-recovery
worker: interrupted claimed analysis can require intervention. Single-instance
review is supported now; leases/fencing, recovery and failover are required before
running multiple orchestrators.

Pending deployment work: non-root app/build images, isolated restricted build
workers without platform credentials, durable shared evidence storage, backups,
identity/roles, TLS, protected metrics, resource/network controls, CI, coverage
thresholds and restart/failover evidence. Flyway clean is disabled; H2 is test-only.

Optional after acceptance: Kubernetes, multi-region deployment, dedicated hardened
build hosts, distributed tracing and external models. Redis/Kafka are not required
for the deterministic assessment path.
