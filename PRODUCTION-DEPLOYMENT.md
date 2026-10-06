# Deployment status

This is a local assessment prototype with runnable intelligence, planning and a
generated create/redirect execution slice. Compose supplies PostgreSQL 16 with a required
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

Local child builds use the checked-in platform wrapper assets and a pinned generated
POM. Set AGENTIC_MAVEN_REPOSITORY to the existing Maven cache location; configure
AGENTIC_BUILD_ASSETS_ROOT when launching outside the project root. Build streams are
bounded and timeouts terminate descendants. This is a trusted deterministic local
generator, not an arbitrary-code execution service. An interrupted claimed engineering
run needs intervention until leases/recovery are implemented. Generated service data
is in memory and cannot be treated as a durable production URL service yet.

Pending deployment work: non-root app/build images, isolated restricted build
workers without platform credentials, durable shared evidence storage, backups,
identity/roles, TLS, protected metrics, resource/network controls, CI, coverage
thresholds and restart/failover evidence. Flyway clean is disabled; H2 is test-only.

Optional after acceptance: Kubernetes, multi-region deployment, dedicated hardened
build hosts, distributed tracing and external models. Redis/Kafka are not required
for the deterministic assessment path.
