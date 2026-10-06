# Agentic Engineering Platform

The primary product is a governed software-engineering platform. A URL-shortener
target will demonstrate its execution behavior. All implementation here is original;
reference archives were inspected without copying their source or history.

## Current checkpoint: foundation (commit 1)

Implemented: Java 21/Spring Boot 3.5.0, Maven Wrapper 3.3.4 with Maven 3.9.11,
PostgreSQL/Flyway schema, transactional requirement submission, workflow inspection,
pending interpretation task, submission audit, strict JSON inputs, OpenAPI,
RFC 9457 Problem Details, health probes and baseline Prometheus instrumentation.

Execution contracts and storage exist. Agents, planning, filesystem access, build
execution, repair, approvals and URL service behavior are not implemented yet.
Submission therefore stays `RECEIVED`, with `executionEnabled=false` and
`sourceMutationAllowed=false`. This checkpoint is not an end-to-end assessment.

## Local setup

Prerequisites: JDK 21 and Docker Desktop with Linux containers. PowerShell commands:

```powershell
Set-Location 'C:\Users\prabh\IdeaProjects\ajay-matta\agentic-url-shortener'
$env:DB_PASSWORD = 'choose-a-local-development-password'
docker compose up -d postgres
.\mvnw.cmd spring-boot:run
```

The database port binds only to localhost. `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
and `PORT` configure the application. There is no embedded production database or
default database password. Compose currently starts PostgreSQL only.

In another PowerShell window:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health/readiness
.\scripts\check-foundation.ps1
```

Documentation: `/swagger-ui.html`, OpenAPI `/v3/api-docs`, liveness
`/actuator/health/liveness`, readiness `/actuator/health/readiness`, metrics
`/actuator/prometheus`.

## Submission API

```powershell
$body = @{
  requirement = 'Create a URL-shortener with HTTP 302 redirects'
  repositoryPath = 'greenfield-url-shortener'
} | ConvertTo-Json
$workflow = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/workflows `
  -ContentType application/json -Body $body
Invoke-RestMethod "http://localhost:8080/api/v1/workflows/$($workflow.workflow.id)"
```

POST returns HTTP 202 and a Location identifying the persisted workflow. The
request accepts only `requirement` and `repositoryPath`. Unknown fields, duplicate
JSON keys, trailing JSON, missing/blank/oversized inputs and scalar coercions are
rejected. No task completion, status update or caller-provided artifact endpoint
exists. `repositoryPath` is stored as a selector; it is not opened or trusted as
a filesystem location in this checkpoint.

## Verification

```powershell
.\mvnw.cmd '-Dtest=WorkflowApiTest,WorkflowPersistenceTest,ExecutionContractTest' test
.\mvnw.cmd clean verify
.\mvnw.cmd -Ppostgres-it clean verify
```

Default verification runs H2-backed API/persistence tests and execution-contract
tests. H2 is test-only and runs the same migration in PostgreSQL compatibility mode.
It does not establish PostgreSQL compatibility. `postgres-it` adds four real
PostgreSQL/Testcontainers checks and requires Docker; missing Docker is a failure,
not a skipped success. Surefire reports: `target/surefire-reports`; PostgreSQL
reports: `target/failsafe-reports`; coverage: `target/site/jacoco/index.html`.
Coverage is reported now; numeric coverage enforcement is scheduled for commit 5.

The Apache Windows wrapper has a local null-safe directory-target lookup adjustment:
regular `.m2` directories can return a null `Target` in Windows PowerShell. Version
3.3.4 and Maven 3.9.11 remain pinned. Verification passed: 43 default tests and
4 real PostgreSQL checks, with no failures/errors/skips. JaCoCo line coverage is
83.09%; the live foundation script passed. See REVIEWER-GUIDE.md for verification
and limits. These checks validate the foundation, not the complete engineering chain.

See [TRACEABILITY.md](TRACEABILITY.md), [REVIEWER-GUIDE.md](REVIEWER-GUIDE.md) and
[MANUAL-ACCEPTANCE.md](MANUAL-ACCEPTANCE.md) for evidence boundaries and review steps.
