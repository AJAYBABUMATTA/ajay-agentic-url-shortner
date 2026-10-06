# Reviewer guide

## Available behavior

Submit a requirement through POST /api/v1/workflows. The worker automatically
interprets it, pauses ambiguity, or analyzes an isolated repository snapshot and
persists a requirement-specific dependency graph. Engineering source generation and
release readiness remain gated. Inspect ARCHITECTURE.md for boundaries/limitations.

Start PostgreSQL/application using README. Then run:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-intelligence.ps1 -OperatorToken 'local-review-operator-token'
```

Expected: distinct greenfield/brownfield plans, source type-reference reasoning,
ambiguity pause, HTTP 401 without operator credentials, authenticated clarification
revision, evidence invalidation, verified inventory reuse and rejected completion
injection. Printed attempts and artifact hashes are persisted server evidence.

## Verification

```powershell
.\mvnw.cmd "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" '-Dtest=RequirementInterpreterTest,RepositoryToolsTest,DynamicPlannerTest,IntelligenceWorkflowTest,ScheduledIntelligenceTest' test
.\mvnw.cmd "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" -Ppostgres-it clean verify
```

Latest inspected clean verification: 75 default tests and six PostgreSQL integration
tests passed (81 total), with zero failures, errors or skips. The native-link
rejection test passed using the available Windows link capability. JaCoCo:
901/957 lines (94.15%), 469/700 branches (67.00%). Live check-intelligence.ps1 passed against the restarted application, including
controller-to-service reasoning, clarification revision and verified reuse.
These Maven totals precede the target-package fix; final clean verification of
the fix and its additional regression test remains pending.

Tests cover strict API inputs, transactional rollback, ownership/hashes, requirement
ambiguity, bounded repository operations, graph variation/cycles, automatic scheduled
dispatch, revision invalidation, upstream-source changes and verified reuse. Real
PostgreSQL checks require Docker and fail if unavailable. Coverage is reported but
not threshold-enforced. Reports live under target/surefire-reports,
target/failsafe-reports and target/site/jacoco.

## Final acceptance still required

Generated service/source/tests must compile and execute through real child builds.
Feature gates must prove connected runtime changes, discovered meaningful generated
tests, policy success and current exact-evidence approvals. Recovery, rejected
approval, rollback, dynamic execution replanning, final images and six demos remain
pending. TRACEABILITY.md maps all PDF requirements without claiming storage/contracts
are completed orchestration. The user runs final git diff --check.
