# Reviewer guide: checkpoint 1

## What to assess now

The caller boundary and durable foundation: a requirement creates a received
workflow and pending interpretation task; callers cannot supply execution results.
Do not assess this checkpoint as a completed agentic platform.

```powershell
.\mvnw.cmd --version
.\mvnw.cmd '-Dtest=WorkflowApiTest,WorkflowPersistenceTest,ExecutionContractTest' test
.\mvnw.cmd clean verify
.\mvnw.cmd -Ppostgres-it clean verify
```

Expected: Java 21 enforcement passes, tests pass with no failures/errors/skips,
and a JaCoCo report is produced. Real PostgreSQL tests require Docker and an
available `postgres:16-alpine` image. The profile fails if Docker is missing;
default H2 test success does not replace PostgreSQL verification.

Start PostgreSQL and the application using README instructions, then run
`scripts/check-foundation.ps1`. Expected: received workflow, pending task, audit
event, HTTP 400 for caller completion injection, HTTP 404 for completion endpoint,
and unchanged task/workflow state. Inspect readiness, OpenAPI and Prometheus.

Inspect `src/main/resources/db/migration/V1__platform_foundation.sql` for all 14
tables and composite evidence ownership constraints. Inspect `execution` for
structured proposal, artifact, build, validation and recovery contracts; none is
advertised as an implemented executor. Inspect `WorkflowPersistenceTest` for
post-write transactional rollback and stale/cross-revision evidence rejection.

## Later acceptance

C3 must demonstrate real generated service/source/tests and child Maven evidence.
C4 must demonstrate recovery, governance and replanning. C5 must rebuild images,
run all six demos and review every traceability row. Approval hashes must identify
the exact reviewed plan/outcome, and generated behavior must reach actual APIs.

The agent performs no Git commands. The user commits each validated checkpoint
and supplies its hash before the next begins. The final user-run `git diff --check`
output is part of acceptance evidence.
