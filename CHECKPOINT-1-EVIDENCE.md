# Checkpoint 1 verification record

Status: implementation prepared; one test configuration fix awaiting verification;
not ready for commit approval.

## Observed evidence

- Target repository was empty before implementation.
- Java executable reported Temurin OpenJDK 21.0.11.
- User-provided console image showed `maven-wrapper-plugin:3.3.4:wrapper`
  `BUILD SUCCESS` and generation for Maven 3.9.11.
- Workspace inspection confirmed wrapper properties and scripts exist.
- The agent's targeted test invocation stopped in wrapper bootstrap with
  `Cannot index into a null array`, before Maven or compilation started.
- Diagnosis identified an unconditional `.Target[0]` access on `.m2` directory
  metadata in the Apache Windows wrapper. The lookup now checks for a target first.

- The next user-provided console image showed compilation and 43 tests executed:
  1 failure, 0 errors, 0 skipped. All seven persistence tests passed. The failed
  API check was HTTP 404 at `/actuator/prometheus`; OpenAPI assertions following
  that request were not reached.
- `WorkflowApiTest` now enables `@AutoConfigureObservability`: Spring Boot tests
  otherwise disable metric registries even with a Prometheus runtime dependency.
  The endpoint assertion remains unchanged; this enables the actual registry.
- The agent attempted the corrected endpoint test once. Maven stopped before test
  execution because its local repository resolved to `C:\.m2\repository`, which
  was inaccessible in this execution session. Local PowerShell verification with
  an explicit user repository location is requested.

Successful full verification, coverage, actual PostgreSQL and live API results
are still pending. The earlier wrapper bootstrap failure is resolved sufficiently
for the user's build to compile and run tests; the current metrics test fix remains
unverified.

## Prepared verification

| Suite | Purpose | Status |
|---|---|---|
| WorkflowApiTest | Strict caller boundary, malformed/oversized input, persisted inspection, probes/OpenAPI/metrics | previous run: metrics check failed; correction pending |
| WorkflowPersistenceTest | Atomic post-write rollback and DB ownership/hash/attempt/restoration constraints | 7 passed in user output |
| ExecutionContractTest | Stale artifacts, proposal lineage, immutable results, real-evidence contracts and bounded recovery | included in 43-test run; inspect rerun report for final totals |
| PostgresFoundationIT | Actual PostgreSQL Flyway migration, readback, dependency FK, caller rejection and readiness | not run; requires Docker |
| check-foundation.ps1 | Live persisted intake and rejected completion operations | not run; requires running platform |

Default `clean verify` produces the JaCoCo report. PostgreSQL is opt-in through
`-Ppostgres-it` and must pass separately. Coverage enforcement and full engineering
execution are later checkpoints. All remaining PDF execution requirements remain
foundation/pending in TRACEABILITY.md.

Proposed commit message after successful verification and user validation:
`feat: establish durable agentic platform and execution contracts`

Only checkpoint 1 is authorized. No Git operations have been performed.
