# Engineering outcomes

The platform returns persisted engineering evidence, not caller-supplied completion.
A supported execution produces requirement criteria/assumptions/risks, repository and
architecture rationale, a dynamic plan/recovery scope, exact proposals/diffs/manifests,
real build/test/coverage reports, validation/policy decisions, attempts, documentation
and security limitations. Each artifact binds revision/task/input hashes.

A candidate outcome has featureComplete=true only when the first nine gates pass;
releaseReady remains false until an authenticated human approves its exact current
hash. Final outcome closes all ten gates and includes criterion-to-production/test
traceability, current manifest/plan/artifact hashes, approvals, policies, attempts,
recovery, assumptions, risks and explicit limits. Its input lineage includes the
immutable approved candidate and approval identity. Approval means engineering review
acceptance; the platform does not deploy generated code.

Compiler/test failures retain failed attempts and real evidence. Supported diagnosis
produces a production-only repair proposal followed by another actual verification.
Unsupported recovery/exhaustion/cancellation restores baseline, records verified hashes
and invalidates outcomes/release approvals. Failure never creates false readiness.
Rejected outcome decisions remain inspectable. Historical rows remain in the database;
current API views exclude invalidated artifacts and approved release decisions.

Upstream or workspace drift cannot reuse old release evidence. Replanning creates a
child revision, invalidates derived evidence/approvals and verifies repository inventory
before reuse. Older stage outcomes lacking the current gates require replanning.

Deterministic capabilities are bounded: greenfield create/redirect and the original
brownfield total/daily analytics fixture. Unsupported intent stops or asks clarification.
Generated state remains in memory; full target persistence, URL features/security,
coverage enforcement and distributed failover remain stage 5.