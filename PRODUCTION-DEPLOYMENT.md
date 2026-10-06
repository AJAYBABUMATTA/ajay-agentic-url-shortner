# Deployment and operational limits

Dockerfile builds the original source with Java 21 and Maven Wrapper. The runtime
retains JDK/build assets because controlled engineering actions compile isolated
projects. App containers run UID/GID 10001, drop capabilities, prohibit privilege
gain, and have read-only roots. /tmp, shared workspaces and per-worker Maven caches
are writable. Generated Maven receives a filtered environment and no platform secrets.
This is an assessment execution boundary, not a sandbox for hostile arbitrary code.

Compose runs PostgreSQL plus orchestrator-a (18080) and orchestrator-b (18081), using
one database and workspace volume. Each revision holds an OS file lock for processing
and a tokenized durable lease with 15-second expiry and 5-second heartbeat. Expired
ownership cannot be taken over while a live worker still holds the workspace lock.
After container death the survivor claims recovery, marks interrupted attempts failed,
records unknown build results as infrastructure failure without fabricated tests or
coverage, restores the exact baseline, invalidates release evidence, and stops safely.
Fresh revision/review is required. Successful/queued durable state survives restart.

The shared volume must support reliable cross-process file locks. Do not substitute an
unverified network filesystem. Container termination also kills child build processes;
for a native JVM crash, terminate orphaned builds before allowing recovery. Database
outage prevents progress; a retained physical lock prevents concurrent stale writers.
Crash recovery restores baseline, rather than promising crash-atomic multi-file writes.
Failed Maven target output can remain for diagnosis and cannot authorize release.

Never remove named volumes to diagnose failures. Back up PostgreSQL and workspace
volumes together. Reuse the database password for existing volumes; changing the env
value alone does not rotate an initialized database. Configured operator tokens must
remain private. Compose binds database, application and monitoring ports to loopback.
Production deployment needs TLS/access control, separate authenticated operator roles,
managed secrets, backup/restore drills, resource quotas, hardened build isolation,
network policy, trusted proxy identity, retention/legal policies and artifact signing.
These are optional operational hardening beyond the documented local assessment.

Build/check/start commands are in README. The final-image six-scenario runner is
scripts/acceptance.ps1. Measured execution status is in REVIEWER-GUIDE.md; the presence
of Docker/CI files alone does not establish deployment acceptance.