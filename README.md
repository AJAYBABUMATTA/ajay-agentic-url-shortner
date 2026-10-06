# Agentic Engineering Platform

A governed engineering platform demonstrated through an original URL-shortener
repository. Requirement interpretation, ambiguity handling, repository reasoning,
revision lineage and dynamic planning execute automatically. After exact plan approval,
a bounded greenfield create/redirect requirement generates production and HTTP tests,
applies validated proposals and runs real Maven verification. Repair and release
approval remain later stages.

## Current behavior

POST a requirement and relative repository selector. A durable intake worker invokes
four deterministic analysis agents, records attempts and hash-bound artifacts, and:

- Pauses ambiguous requirements in AWAITING_CLARIFICATION without creating a workspace.
- Snapshots clear requirements into an isolated revision workspace, analyzes actual
  source types/routes/data-flow candidates, derives criterion-specific dependencies,
  and stops at AWAITING_CHANGE_APPROVAL.
- Creates child revisions for authenticated clarification/replanning, invalidates
  requirement-derived evidence and approvals, and reuses repository inventory only
  after an unchanged baseline manifest is verified.

Authenticated CHANGE approval binds the exact current plan hash and queues automatic
engineering execution. executionEnabled/sourceMutationAllowed are true only while
the workflow is EXECUTING; mutation is limited to its isolated workspace. A successful
slice enters AWAITING_RELEASE_APPROVAL with releaseReady=false. Release approval is
not exposed yet. Unsupported generators or failed builds enter SAFE_STOPPED.

## Setup

Requires JDK 21, Docker Desktop (Linux containers), and PowerShell. Maven Wrapper
3.3.4 pins Maven 3.9.11. Spring Boot is pinned at 3.5.0.

```powershell
Set-Location 'C:\Users\prabh\IdeaProjects\ajay-matta\agentic-url-shortener'
$env:DB_PASSWORD = 'your-existing-local-database-password'
$env:AGENTIC_OPERATOR_TOKEN = 'local-review-operator-token'
$env:AGENTIC_MAVEN_REPOSITORY = "$env:USERPROFILE\.m2\repository"
docker compose up -d postgres
.\mvnw.cmd "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" spring-boot:run
```

Use the password that initialized the existing database. Operator tokens are
configured explicitly; no default authorization secret exists. Environment settings:
DB_URL, DB_USERNAME, DB_PASSWORD, PORT, AGENTIC_OPERATOR_TOKEN,
AGENTIC_REPOSITORY_ROOT (default ./scenario-repositories), and AGENTIC_WORKSPACE_ROOT
(default ./agent-workspaces). Database/workspace roots must be distinct.

In another terminal:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-intelligence.ps1 -OperatorToken 'local-review-operator-token'
```

Expected: automatic greenfield/brownfield plans, authenticated ambiguity revision,
evidence invalidation, verified inventory reuse, and blocked engineering mutation.
The script prints persisted graph, repository reasoning, attempts and hashes.

## API

| Method/path | Caller inputs | Result |
|---|---|---|
| POST /api/v1/workflows | requirement, repositoryPath | HTTP 202 intake; worker automatically interprets/plans |
| GET /api/v1/workflows/{id} | workflow ID | Current revision, graph, audit, analyses, artifact hashes and attempts |
| POST /api/v1/workflows/{id}/clarifications | expectedRevision, answers keyed by question IDs | Authenticated child revision; stale revision returns 409 |
| POST /api/v1/workflows/{id}/replan | expectedRevision, reason, optional replacement requirement | Authenticated new revision and automatic fresh planning |
| POST /api/v1/workflows/{id}/change-approvals | expectedRevision, planHash, decision, reason | Authenticated exact-plan approval queues execution; rejection safely stops |
| GET /api/v1/workflows/{id}/engineering | workflow ID | Full proposals, diffs, manifests, build logs, coverage and bounded slice outcome |

Clarification/replanning/change approval require X-Operator-Id and X-Operator-Token. Absent/wrong
credentials return 401 when configured; missing server authorization configuration
returns 503. Unknown fields, duplicate keys, scalar coercions and manual completion
are rejected. No caller can supply tasks, file proposals, validation or success state.

Supported offline capabilities: create, redirect, aliases, expiry, inspection,
deactivation, total/UTC daily analytics, rate-limit policies and measurable
performance targets. Planning a capability does not establish an implemented
engineering agent for it. Unknown domains, vague intent, missing units/windows or
conflicting policies require clarification; unsupported generation must safely stop.

Swagger /swagger-ui.html; OpenAPI /v3/api-docs; health /actuator/health/liveness and
/actuator/health/readiness; baseline metrics /actuator/prometheus.

## Verification

```powershell
.\mvnw.cmd "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" '-Dtest=RequirementInterpreterTest,RepositoryToolsTest,DynamicPlannerTest,IntelligenceWorkflowTest,ScheduledIntelligenceTest' test
.\mvnw.cmd "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" -Ppostgres-it clean verify
```

H2 is test-only; PostgreSQL profile adds six real database checks, three nested
Maven engineering checks (success, compiler failure, HTTP-test failure), and an
HTTP-only automatic execution scenario on PostgreSQL. Verification fails when
Docker is unavailable. Reports: target/surefire-reports, target/failsafe-reports,
and target/site/jacoco/index.html. Coverage is reported; threshold enforcement is
pending. Current verified results and remaining checks are in REVIEWER-GUIDE.md.

Reference code/history was not copied. The Windows wrapper has a null-safe directory
metadata lookup adjustment. No external model or model-provided command is used.

## Generated greenfield service

Generation supports a Markdown-only baseline and exactly create/redirect criteria.
The original source fixture is unchanged. Ten generated files include an executable
Spring Boot service, platform-owned Maven build assets and two HTTP-test classes.
The requested redirect status (301 or 302) is reflected in production and tests.

Run the demo once to inspect the persisted plan:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\demo.ps1 greenfield
```

Review the printed plan, then approve its exact values:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\demo.ps1 greenfield -WorkflowId '<printed-id>' -ApprovedPlanHash '<printed-hash>' -OperatorToken 'local-review-operator-token'
```

Expected: four compiled production files, five executed HTTP tests, persisted line
coverage and criterion traceability, and releaseReady=false. Full contents/logs are
available through GET /api/v1/workflows/{id}/engineering. Generated service storage
is in memory; persistence, aliases, expiry, analytics, rate limits and production
security are pending. Failed child builds persist their classification and stop;
automatic repair/retry and whole-workflow rollback are planned for the next stage.
