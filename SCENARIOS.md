# Scenarios

Run scripts/check-intelligence.ps1 against the current PostgreSQL-backed application.
It performs API-driven submission, polling and authenticated revision updates.

| Scenario | Current evidence | Remaining engineering proof |
|---|---|---|
| Greenfield | Automatic criteria/plan; exact-plan approval; generated create/redirect runtime; real child build and HTTP tests | Full feature gate, release governance and production hardening |
| Brownfield | Actual controller/service reference map and criterion-specific impacts | Enhancement of connected redirect/analytics runtime path |
| Ambiguous | No repository/workspace stage; authenticated expiry clarification creates child revision | Integration with complete engineering execution |
| Replanning | Requirement change invalidates derived artifacts; unchanged verified inventory reused | Execution-stage recovery/replanning after downstream failure |
| Repository failure | Unsafe/unavailable input enters SAFE_STOPPED | Governed rollback/retry policies |

Automated tests also change upstream repository source and prove inventory reuse
is rejected when the manifest changes. No human provides node output or completion.
Scripts print real persisted IDs, criteria, graphs, hashes and attempts.

check-foundation.ps1 still verifies the caller boundary while allowing automatic
analysis progress. check-intelligence.ps1 verifies the complete planning stage.
demo.ps1 greenfield now performs plan inspection and a second invocation with exact
hash approval, then prints persisted generated build/outcome evidence. Other demo
scenarios arrive with their implementations and final-image acceptance.
Repair, release approval rejection, durable baseline restoration and two-worker failover
are not implemented or demonstrated yet.

GeneratedSliceIT runs genuine child compiler and HTTP-test failures via test-only
provider fault injection; the process is real. Both persist failure evidence and
SAFE_STOPPED without a successful outcome. These tests do not claim automatic repair.
