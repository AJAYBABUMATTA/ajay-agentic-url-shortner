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
numeric coverage checks and durable worker recovery are implemented; final container evidence is recorded in REVIEWER-GUIDE.md.
Complete greenfield outcomes link nine behavioral criteria to exact production
capability changes and canonical generated HTTP tests. Eleven original production
files are compiled; coverage enforcement is part of fixed clean verify. The full target
uses PostgreSQL/Flyway in production and H2/DNS fixtures for generated tests; separate
PostgreSQL HTTP tests verify transactions, contention and exact counter totals.
Failover never emits a passing engineering outcome: it preserves unknown failure
classification, verified rollback and human-intervention evidence. The operator must
inspect and approve fresh revision evidence. Outcome readiness does not deploy code.