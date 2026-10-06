# Deployment status

The current assessment prototype supports local single-instance execution, parallel
agent branches, bounded recovery and exact evidence governance. Compose supplies
PostgreSQL 16 with a required password and durable volume; the application runs through
Maven. Final non-root application/build images, CI and two-orchestrator failover belong
to stage 5 and are not yet available.

Configure DB_URL, DB_USERNAME, DB_PASSWORD, AGENTIC_OPERATOR_TOKEN and distinct approved
repository/workspace roots. Use the password that initialized an existing database.
Set AGENTIC_MAVEN_REPOSITORY to the existing dependency cache and
AGENTIC_BUILD_ASSETS_ROOT when launching outside the project directory. Flyway clean
is disabled. H2 is test-only. Do not commit secrets or generated runtime workspaces.

Queue claims prevent simultaneous execution of one queued run, but do not recover a
crash after claim. A running build is cancellable; process interruption/crash requires
intervention until durable leases/fencing/restart recovery are added. PostgreSQL stores
ledger evidence; workspace/baseline files also need durable storage. Per-file writes
and handled-failure rollback do not establish crash-atomic filesystem/database writes.

Builds use a pinned platform POM/wrapper and a filtered environment, not arbitrary
model commands. NIO guards are not full hostile-code isolation. Production needs
restricted build workers/mounts/resources/network, independent identities/roles,
protected read APIs/metrics, TLS, backups and credential management. Generated service
data is in memory, so this release outcome is an engineering-review decision rather
than an authorization to deploy a durable public URL service.

Rubric-critical stage 5: complete URL APIs/security/persistence/concurrency/cleanup,
coverage enforcement, non-root images, two workers and durable failover, CI, final-image
six demos and complete PDF acceptance. Optional later hardening: Kubernetes, multi-region,
dedicated hardened build hosts, distributed tracing and external model providers.