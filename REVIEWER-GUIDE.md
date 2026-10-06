# Reviewer guide

The primary product is the agentic engineering platform. Submit a requirement, inspect
its dynamically generated plan and exact recovery scope, then approve the current plan
hash. The platform invokes agents and tools automatically. Review full generated
production/test proposals, manifests/diffs, actual compiler/test/coverage logs, gates,
policies and outcome before approving or rejecting its exact outcome hash.

## Commands

Start PostgreSQL and the current application using README. Run any scenario:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\demo.ps1 greenfield
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\demo.ps1 brownfield
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\demo.ps1 ambiguous
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\demo.ps1 repair
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\demo.ps1 safe-stop
```

Each prints actual IDs/hashes and a continuation command. Review first, then execute
that command with your configured token. Ambiguous pauses without a workspace and
prints a clarification command. Safe-stop expects a genuine failed compiler build,
unsupported diagnosis and verified rollback. Other scenarios expect passing real builds
and releaseReady=false until exact outcome approval. -ReleaseDecision REJECTED restores
baseline and retains the rejection. Final-image failover demo remains stage 5.

```powershell
.\mvnw.cmd "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" '-Dtest=EngineeringWorkflowTest,RecoveryPolicyTest,ProposalToolTest,MavenBuildToolTest' test
.\mvnw.cmd "-Dmaven.repo.local=$env:USERPROFILE\.m2\repository" -Ppostgres-it clean verify
```

Full clean verification on Java 21 completed with BUILD SUCCESS: 96 default tests
and 19 integration tests, 115 total; zero failures, errors or skips. A final generated
brownfield documentation correction was followed by targeted Maven verification:
11 boundary/recovery unit cases plus the actual brownfield build/approval/rollback
scenario, all 12 passing. These repeated checks are not added to the distinct total.

Current JaCoCo report after that final recompile/check: 1,668/1,779 lines (93.76%)
and 1,139/1,583 branches (71.95%). The preceding full-clean report was 95.27% lines
and 73.59% branches; recompiling the provider for its documentation correction means
its earlier execution data is excluded from the current report. Coverage enforcement
is still stage 5. Full command output is retained locally in ignored
commit4-final-verification.log; the final targeted check is in
commit4-documentation-verification.log. Targeted execution replaces that class's XML
report; rerun clean verify for one fresh complete report set.

Runtime inspection verified 148 retained engineering artifact hashes and 67 final
proposal contents against manifests, with zero mismatches. The HTTP/PostgreSQL final
outcome passed all ten gates. Its actual Prometheus export is retained in
target/stage4-evidence/postgres-metrics.prom, including release success rate 1.0.
Reports: target/surefire-reports, target/failsafe-reports and target/site/jacoco.
Coverage is reported; enforcement remains stage 5.

## Runtime evidence

RecoveryGovernanceIT executes real generated builds: brownfield analytics, missing
bootstrap repair, production redirect-test repair, unsupported fallback, exhausted
three-build bound, release rejection, upstream invalidation/replan, cancellation and
ambiguity clarification. A latch proves independent architecture/security-design
agents overlap; shared brownfield paths preserve expected predecessor hashes.
GeneratedApiIT uses real HTTP/PostgreSQL and automatic scheduling through exact plan
and release approvals. GeneratedSliceIT exercises genuine compiler and assertion
failures; failed tests cannot be disguised as successful outcomes.

Retained full exports are in target/stage4-evidence. Inspect FILE_PROPOSAL content,
UNIFIED_DIFF, current MANIFEST, BUILD_EVIDENCE logs, DIAGNOSIS, conditional TASK_PLAN,
repair linkage, policy/approval rows, rollback hashes and final outcome. Test databases
and workspaces are temporary: paths inside exports identify execution locations but
can disappear after tests finish. Local demos retain persistent database/workspace
state. Read engineering GET for full evidence; workflow GET for DAG/attempt/audit lineage.

## Assessment limits

Generation supports exactly greenfield create/redirect or the original supported
brownfield total/daily analytics fixture. Other planned capabilities safely stop.
Known recovery fixes only supported production defects; repairs cannot weaken tests
or alter build configuration. Generated service data is in memory. OS isolation,
independent operator identities, full URL security/persistence/features, numeric
coverage enforcement, CI, final images and distributed restart/failover remain stage 5.
TRACEABILITY.md maps every PDF row to implementation/tests/evidence/commands/status.
The user owns Git operations and final git diff --check.