# Deployment status and controls

This checkpoint is a local-review foundation, not an approved production deployment.
`compose.yaml` provides PostgreSQL 16 with a named durable volume, healthcheck,
required environment password and localhost-only published port. The application
runs through Maven against PostgreSQL. No application image is supplied yet.

Set DB credentials in the process environment. Do not commit `.env`, secrets,
workspace outputs or build logs. Flyway clean is disabled. H2 is test-only.

Before deployment, C5 must provide non-root application/build images, two
orchestrators, shared durable artifact/workspace storage, worker leases and fencing,
restart/failover evidence, CI, database backup/restore guidance and resource controls.
Authentication/authorization, least-privilege database roles, TLS, protected metrics
and API access must be configured for the deployment environment.

Build workers execute repository build logic. They must not inherit platform,
operator, model or production credentials. Network/CPU/memory/time/file bounds
and isolation are required in addition to fixed command capabilities.

Optional hardening after assessment acceptance: Kubernetes, multi-region operation,
external identity federation, distributed tracing and dedicated hardened build hosts.
No Redis, Kafka or external model service is needed for the deterministic acceptance path.
