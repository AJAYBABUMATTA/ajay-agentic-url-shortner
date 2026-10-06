# Scenarios

## Available now: foundation intake

Run `scripts/check-foundation.ps1` against a running platform. It submits a
requirement, reads its persisted workflow/revision/task/audit, checks that execution
and mutation are disabled, attempts an injected completion field and confirms HTTP
400, then attempts an unsupported task-completion operation and confirms HTTP 404.
The task remains pending. This is caller-boundary evidence, not engineering execution.

## Planned scenario acceptance

| Scenario | Required result | Checkpoint |
|---|---|---|
| Greenfield | Requirement generates runnable service/source/tests; actual compiler and discovered tests pass | C3 |
| Brownfield | Existing runtime path changes; generated tests prove new behavior and regression compatibility | C4 |
| Ambiguous | Mutation blocked; authenticated clarification creates child revision and new plan | C2/C4 |
| Repair | Actual compiler/test failure produces diagnosis and guarded repair, then a passing real build | C4 |
| Approval rejection | Exact evidence rejected by an authorized human; execution/release gate stays closed | C4 |
| Safe stop / rollback | Bounded failure or cancellation stops safely; restoration verified by baseline manifest | C4 |
| Replanning | Upstream change invalidates dependent evidence/approvals and changes the graph | C4 |
| Failover | Second orchestrator safely recovers work without accepting stale worker writes | C5 |

`demo.ps1 greenfield`, `brownfield`, `ambiguous`, `repair`, `safe-stop` and `failover`
will be delivered and run against final images in C5. They do not exist yet.
