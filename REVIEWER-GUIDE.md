# Reviewer guide

## Available behavior

Submit a requirement through POST /api/v1/workflows. The worker automatically
interprets it, pauses ambiguity, or analyzes an isolated repository snapshot and
persists a requirement-specific dependency graph. Exact-plan approval enables the
bounded greenfield create/redirect generator, controlled patches and real builds.
Release readiness remains gated. Inspect ARCHITECTURE.md for boundaries/limitations.

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

Latest clean verification on Java 21: BUILD SUCCESS; 90 default tests and 10
integration tests passed (100 total), with zero failures, errors or skips.
JaCoCo: 1,330/1,400 lines (95.00%), 832/1,164 branches (71.48%). Coverage is
reported, not threshold-enforced yet. The target-package exclusion regression passed.

The generated 301 and 302 slices each compiled four production files and executed
five HTTP tests. Each generated successful build reported 25/27 covered lines
(92.59%). A genuine compiler failure returned exit 1/COMPILATION with no executed
tests; a genuine HTTP assertion failure returned exit 1/TEST with five executed
cases and one failure. Both stopped without successful outcomes. These expected
child failures are passing platform acceptance tests, not ignored build errors.

Retained API/build exports live in target/stage3-api-evidence: workflow.json,
engineering.json (HTTP/PG 301 run), greenfield-302.json, compiler-failure.json and
http-test-failure.json. Their full proposal contents, manifests, diffs, logs, hashes
and outcomes were inspected. All 12 successful engineering artifact hashes matched;
all 10 generated operation contents matched the applied manifest, and the outcome
manifest matched the applied patch. Integration databases/workspaces are temporary;
paths inside these exports identify where execution occurred and can disappear
when tests end. Run the API demo for persistent local database/workspace evidence.

Tests cover strict API inputs, transactional rollback, ownership/hashes, requirement
ambiguity, bounded repository operations, graph variation/cycles, automatic scheduled
dispatch, revision invalidation, upstream-source changes and verified reuse. Real
PostgreSQL checks require Docker and fail if unavailable. Coverage is reported but
not threshold-enforced. Reports live under target/surefire-reports,
target/failsafe-reports and target/site/jacoco.

## Final acceptance still required

The generated create/redirect slice now compiles and executes HTTP tests through real
child builds. Complete feature gates must prove connected runtime changes, meaningful
tests, policy success and current exact-evidence approvals. Recovery, rejected
approval, rollback, dynamic execution replanning, final images and six demos remain
pending. TRACEABILITY.md maps all PDF requirements without claiming storage/contracts
are completed orchestration. The user runs final git diff --check.

## Engineering slice review

Run scripts/demo.ps1 greenfield to inspect its persisted plan, then rerun with the
printed WorkflowId and ApprovedPlanHash plus the configured OperatorToken. README
provides exact PowerShell commands. Approval governs the current revision/hash;
wrong hashes, stale revisions, missing credentials and repeated decisions are rejected.
The worker invokes generators and fixed tools automatically; no caller supplies file
content, build commands, task completion or success claims.

Inspect GET /api/v1/workflows/{id}/engineering for full architecture, production/test
proposals, applied manifest, unified diff, build logs, coverage, security/docs and
criterion evidence. The workspace path contains the runnable generated Boot jar.
Expect four compiled production files, five executed HTTP cases and releaseReady=false.
The release task remains AWAITING_APPROVAL. Brownfield enhancement and extra service
features are not generated yet; approval of an unsupported plan safely stops.

EngineeringWorkflowTest checks exact-hash authentication/approval/rejection, unsupported
safe stop and structured agent outputs. ProposalToolTest checks optimistic conflicts,
duplicate/traversal/type/size rejection, exact writes and partial-batch restoration.
MavenBuildToolTest checks discovered/skipped/failed cases, compiled class evidence,
coverage parsing and rejection of untrusted build assets. GeneratedSliceIT runs three
real child builds: passing HTTP slice, compiler failure and HTTP assertion failure.
GeneratedApiIT uses real HTTP and PostgreSQL, automatic scheduling and requested 301
behavior. Successful test evidence alone does not imply full assessment completion.
