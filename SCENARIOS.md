# Scenarios

All demos use the API and print real persisted evidence. Start the latest application
from README. Run scripts/demo.ps1 with a scenario, review its printed plan, continue
with the exact WorkflowId/ApprovedPlanHash, then review its exact outcome before the
optional ApprovedOutcomeHash continuation. Replace only the configured token in the
printed commands; actual IDs/hashes are included automatically.

| Scenario | Requirement and evidence |
|---|---|
| greenfield | Markdown baseline; four compiled production files, five generated HTTP tests; requested 301/302; exact outcome release gate |
| brownfield | Existing UrlController -> UrlService; total and UTC daily/query-day analytics; three production files compiled, six generated HTTP tests and one retained unit test |
| ambiguous | Conflicting 301/302 pauses without workspace; -WorkflowId and -Clarification 301/302 creates authenticated child revision, then full engineering |
| repair | Original fixture has MissingApplication.class; genuine compiler failure, diagnosis, scoped bootstrap UPDATE, second clean verify; seven executed tests |
| safe-stop | Original fixture has unsupported UnknownApplication.class; real compiler failure, unsupported diagnosis, fallback and verified baseline restoration; no releasable outcome |

For release rejection, use -ReleaseDecision REJECTED with the printed exact outcome
hash. Inspect the returned rejection and restored manifest. Authenticated cancel,
safe-stop and rollback endpoints accept expectedRevision and reason. Cancellation
during actual Maven verification is tested; it kills the child before restoring files.

RecoveryGovernanceIT also injects an incorrect 418 into production only: unchanged
HTTP tests genuinely fail, diagnosis repairs production status, and real verification
passes. Another test keeps the production compiler defect through two repair proposals
and proves the third failed build stops. Fault injection exists only in test providers;
public callers cannot submit faulty outputs or completion text.

Upstream changes invalidate candidate release evidence. Authenticated /replan creates
a fresh requirement-specific revision and invalidates derived artifacts/approvals;
changed source prevents repository-map reuse. Existing intelligence tests additionally
cover unchanged-manifest reuse and graph variation/cycles.

The failover demo requires the final Compose pair. Final-image acceptance status is in REVIEWER-GUIDE.md. Current
local demos do not claim distributed crash recovery or production readiness.
## Final image scenarios

Greenfield now declares all nine capabilities and generates the complete persistent
service (eleven production classes, nine HTTP tests). Minimal greenfield remains
available by submitting only creation/redirect requirements. Brownfield intentionally
retains the original in-memory runtime while connecting total and UTC analytics.
Ambiguous pauses and creates a clarified child revision before any mutation. Repair
uses genuine failed compiler evidence; safe-stop restores an unsupported failure.

For failover, run demo.ps1 failover -BaseUrl http://localhost:18080. Review and approve
the printed exact plan. Its continuation waits for a RUNNING validate-build task,
abruptly terminates its owning Compose service, reads recovery from the survivor,
requires a failover audit and verified baseline rollback with no outcome, then restarts
the terminated service. Peer URL defaults to localhost:18081; supply ComposeProject
when using another Compose project name. Fresh review is required after recovery.

scripts/acceptance.ps1 runs all six disposable fixtures through their real APIs,
exports plans and persisted outcomes, and records its automated test identity. Optional
-ApproveFixtureOutcomes tests all ten gates with explicit fixture approvals. Ordinary
interactive demos never implicitly approve a plan or outcome. These fixtures use only
original source from scenario-repositories; execution never modifies that directory.