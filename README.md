# Agentic Engineering Platform

A runnable agentic SDLC prototype that interprets requirements, analyzes an isolated
repository, plans a dynamic task graph, invokes deterministic engineering agents,
applies exact validated file proposals, compiles production, executes discovered
HTTP tests, diagnoses failures, repairs supported defects, and requests approval of
an immutable engineering outcome. The URL shortener demonstrates these actions.

## Run locally

Requires Java 21, Docker Desktop with Linux containers, and PowerShell. Wrapper 3.3.4
pins Maven 3.9.11; Spring Boot 3.5.0. Preserve the password of an existing database.

```powershell
Set-Location 'C:\Users\prabh\IdeaProjects\ajay-matta\agentic-url-shortener'
$env:DB_PASSWORD = 'your-existing-local-database-password'
$env:AGENTIC_OPERATOR_TOKEN = 'local-review-operator-token'
$env:AGENTIC_MAVEN_REPOSITORY = "$env:USERPROFILE\.m2\repository"
docker compose up -d postgres
.\mvnw.cmd "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" spring-boot:run
```

Restart the application after building new source. In another terminal, run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\demo.ps1 greenfield
```

The demo prints actual workflow IDs, plan hashes, and a copyable continuation command.
Review the persisted plan and recovery scope, then use that command with your configured
operator token. After engineering completes, review the returned production/test
proposals, diffs, logs, coverage and outcome. The script prints a second continuation
command to approve or reject the exact outcome hash. It never auto-approves unseen
plan/outcome evidence. RELEASE_READY authorizes review acceptance; it does not deploy.

Supported demos: greenfield, brownfield, ambiguous, repair, safe-stop. Brownfield adds
total and UTC daily analytics to the existing UrlController/UrlService runtime and
executes six generated HTTP cases plus the original unit test. Repair starts from an
original fixture with a real missing bootstrap type: the compiler fails, diagnosis
identifies that production file, the repair agent proposes its complete replacement,
and a second real clean verify passes. Safe-stop uses an unsupported compiler defect,
restores the baseline and produces no releasable outcome. Failover remains stage 5.

## API and caller boundary

| Method/path suffix under /api/v1/workflows | Inputs / result |
|---|---|
| POST (collection) | requirement, repositoryPath; automatic intake and planning |
| GET /{id} | revision, task graph, analyses, attempts, audit and hashes |
| POST /{id}/clarifications | expectedRevision, answers keyed by outstanding question IDs |
| POST /{id}/replan | expectedRevision, reason, optional replacement requirement |
| POST /{id}/change-approvals | expectedRevision, planHash, APPROVED/REJECTED, reason |
| POST /{id}/release-approvals | expectedRevision, outcomeHash, APPROVED/REJECTED, reason |
| POST /{id}/cancel, /safe-stop, /rollback | expectedRevision, reason |
| GET /{id}/engineering | full proposals, manifests/diffs, build evidence, gates, approvals, recovery and rollback |

Governed actions require X-Operator-Id and X-Operator-Token. Unknown inputs, caller
completion/evidence and arbitrary commands are rejected. Wrong revision/hash returns
409; missing credentials returns 401; unconfigured server token returns 503. Ambiguity
pauses before repository access. Replanning invalidates derived evidence/approvals;
repository inventory is reused only after exact unchanged-manifest verification.

Swagger: /swagger-ui.html; OpenAPI: /v3/api-docs; health probes:
/actuator/health/liveness and /actuator/health/readiness; /actuator/prometheus.
Configurable roots: AGENTIC_REPOSITORY_ROOT (./scenario-repositories),
AGENTIC_WORKSPACE_ROOT (./agent-workspaces); these must be separate. Other settings:
DB_URL, DB_USERNAME, DB_PASSWORD, PORT, AGENTIC_BUILD_ASSETS_ROOT,
AGENTIC_MAVEN_REPOSITORY. agentic.execution.parallelism defaults to 4, bounded 1–8.

## Verification and scope

```powershell
.\mvnw.cmd "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" -Ppostgres-it clean verify
```

Real PostgreSQL tests require Docker and fail when unavailable. Nested Maven builds
execute actual generated code/tests. Reports: target/surefire-reports,
target/failsafe-reports, target/site/jacoco and target/stage4-evidence. Latest counts
are recorded in REVIEWER-GUIDE.md after verification. Test workspaces/databases are
temporary; API demos retain evidence in your configured local state.

Generation is intentionally bounded to greenfield create/301-or-302 redirect and the
supported original brownfield total/daily analytics layout. Planning other capabilities
does not imply available implementation. Unsupported recovery stops safely. Generated
service state is in memory. Complete URL persistence/features/security, coverage
threshold enforcement, app images, CI and distributed restart/failover are stage 5.
Reference code/history was not copied; all fixture and agent code is original.