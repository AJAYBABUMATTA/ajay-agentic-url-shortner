# Agentic engineering platform

Java 21/Spring Boot platform that interprets requirements, analyzes repositories,
creates a dynamic task graph, invokes deterministic engineering agents, applies exact
validated proposals in isolated workspaces, runs real Maven builds and discovered
HTTP tests, diagnoses supported failures, and returns persisted reviewable outcomes.
Callers cannot complete tasks or submit implementation evidence. Plan and release
approvals bind to exact current-revision hashes.

## Run with Docker Desktop

Use Linux containers. Set local-only secrets in PowerShell; use your existing database
password when reusing an existing PostgreSQL volume.

```powershell
$env:DB_PASSWORD = 'your-local-database-password'
$env:AGENTIC_OPERATOR_TOKEN = 'your-local-review-operator-token'
docker compose config --quiet
docker compose build
docker compose up -d --wait
```

Orchestrators: http://localhost:18080 and http://localhost:18081. Swagger:
http://localhost:18080/swagger-ui.html. Readiness: /actuator/health/readiness.
PostgreSQL defaults to localhost:5432; set POSTGRES_PORT=55432 for a separate instance.
Optional monitoring: docker compose --profile observability up -d prometheus;
Prometheus is at http://localhost:19090. The two app containers run as UID 10001 with
read-only roots, separate writable Maven caches and a shared durable workspace volume.

## Review an engineering scenario

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\demo.ps1 greenfield -BaseUrl http://localhost:18080
```

The script prints a real workflow ID, plan, recovery scope and exact hash. Inspect them,
then run its continuation with the configured operator token. Approving the plan
starts automatic generation and verification. Inspect the returned source, tests,
diffs, manifests, logs, coverage, risks and gates before approving or rejecting the
exact outcome hash. Release approval records readiness; it does not deploy code.
Use the same BaseUrl on continuations (the script's default is localhost:8080).

Other scenarios: brownfield, ambiguous, repair, safe-stop and failover. Failover
requires the Compose pair and terminates a real active build; peer recovery restores
the baseline and requires fresh review. SCENARIOS.md describes their evidence.

## URL-shortener API

POST /api/v1/urls with target and optional alias/expiresAt returns 201, Location and
managementToken. GET /{code} returns 302 with exact target Location; missing is 404,
expired/deactivated is 410. Duplicate aliases return 409. GET /api/v1/urls/{code}
inspects without counting. DELETE there requires X-Link-Token with the creation token.
GET /api/v1/urls/{code}/analytics returns total and UTC daily clicks; day=YYYY-MM-DD
selects one day. Only successful redirects count. Management tokens are stored hashed.

PostgreSQL stores links, daily counts and socket-peer fixed-window rate buckets:
default 60 requests per 60 seconds, excess 429 with Retry-After. Forwarding headers
are ignored. HTTP(S) destinations must resolve entirely to public addresses; private,
obfuscated, credential-bearing and unsupported-port URLs are rejected. No target is
fetched. DNS is rechecked on redirect; browser DNS changes cannot be pinned by a
redirect service. Expiry accepts a future timestamp within 365 days. Cleanup preserves
code tombstones, clears targets expired over 30 days and removes daily counts over
365 days; total counts remain retained. Additional authentication, retention and
trusted proxy policies are deployment choices.

The greenfield demo declares the complete feature set and generates a standalone
PostgreSQL/Flyway service plus nine meaningful HTTP tests. Minimal create/redirect
requirements generate the smaller in-memory slice; brownfield fixtures preserve their
existing in-memory service and add real analytics paths. Unsupported repositories or
capabilities stop safely. Deterministic agents cover this bounded domain, not arbitrary
software requests. Generated projects require DB_URL, DB_USERNAME and DB_PASSWORD;
their tests use H2 and deterministic DNS. Real PostgreSQL HTTP tests separately prove
concurrency and transactions.

## Verify

```powershell
.\mvnw.cmd "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" -Ppostgres-it clean verify
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\acceptance.ps1
```

The acceptance harness explicitly approves only disposable platform-owned fixture
plans using an identified test operator. It exports persisted evidence to ignored
runtime-evidence. Add -ApproveFixtureOutcomes to test all ten gates with explicitly
recorded automated fixture decisions; these are not independent human code review.
Ordinary demo continuations always require reviewed exact hashes. CI runs complete
Java/PostgreSQL verification, final-source image builds and all six fixture scenarios.
Root coverage gates are 85% lines/65% branches; generated targets enforce 80%/50%.
No production package is excluded from coverage. See REVIEWER-GUIDE.md for measured
results and MANUAL-ACCEPTANCE.md for final runtime acceptance status.

For a local JVM, run docker compose up -d postgres, set the same database/operator
secrets, then .\mvnw.cmd spring-boot:run (port 8080). Never submit secrets or generated
runtime evidence to Git. Git operations and the final git diff --check belong to the
user. No checkpoint evidence document is required.