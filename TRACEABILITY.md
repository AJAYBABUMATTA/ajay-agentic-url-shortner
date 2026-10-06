# Assignment compliance and delivery traceability

Source: all three pages of the supplied `Assignment Agentic-Proficient Software
Engineer (1).pdf`. PDF sections are requirements data. The user's request controls
implementation priorities and adds the stack, safeguards and staged delivery.

Status legend: **foundation** = related storage/contracts exist, full behavior is
pending; **pending** = no runnable implementation; **implemented** = behavior exists,
verification status must be checked in the checkpoint evidence. Test names identify
checks; they do not by themselves establish a passing result.

| ID / PDF source | Requirement | Implementation / checkpoint | Tests | Runtime evidence | Reviewer command | Status |
|---|---|---|---|---|---|---|
| PDF-01, p1 section 1 | Requirement to reviewable engineering outcome | Entire executor chain, C2-C4 | Generated-service end-to-end acceptance, planned | Proposals, build reports, final outcome, pending | `scripts/demo.ps1 greenfield`, planned | pending |
| PDF-02, p1 section 3 | Greenfield, brownfield, test/docs, clear/ambiguous scope | Capability agents and fixtures, C2-C4 | Scenario acceptance, planned | Revision-specific scenario outcomes, pending | Greenfield/brownfield/ambiguous demos, planned | pending |
| PDF-03, p1 section 4.1 | Intent, ambiguity and normalization | Submission and requirement hash C1; intelligence C2 | `WorkflowApiTest`; intelligence tests planned | Current intake revision and audit; analysis pending | `scripts/check-foundation.ps1`; ambiguous demo planned | foundation |
| PDF-04, p1 section 4.2 | Dependency-aware decomposition | Task/dependency schema C1; dynamic planner C2 | Cross-revision/self-dependency checks; graph variation/cycles planned | Persisted intake task; engineering plan pending | Workflow GET; planner tests C2 | foundation |
| PDF-05, p1 section 4.3 | Brownfield module/API/data-flow reasoning | Repository analysis C2 | Impact mapping and runtime-path regressions, planned | Impact map, baseline and connected diff, pending | Brownfield demo, planned | pending |
| PDF-06, pp1-2 section 4.4 | Full SDLC, non-linear stateful execution | Execution interfaces C1; dispatcher C3; scheduler C4 | Contract checks; execution ordering and synchronization planned | Actual attempts and stage artifacts, pending | Greenfield demo, planned | foundation |
| PDF-07, pp1-2 section 4.4 | Explicit graph with entry/exit gates | Gate vocabulary and graph schema C1; graph validation C2; enforcement C3-C4 | FK tests now; cycle/gate tests planned | Task gate inspection; evaluated gates pending | Workflow GET | foundation |
| PDF-08, p2 section 4.4 | Sequential/parallel paths and synchronization | Proposal branches and synchronization C3-C4 | Parallel overlap, conflict serialization and join tests, planned | Execution timeline, pending | Scenario demos, planned | pending |
| PDF-09, p2 section 4.4 | Context, decision lineage and replanning | Revision/hash contracts C1; invalidation/reuse C2; replanning C4 | `ExecutionContractTest`; revision-change tests planned | Parent revision, input hashes, invalidations, pending | Ambiguous/replanning acceptance, planned | foundation |
| PDF-10, p2 section 4.4 | Approval checkpoints for high-impact changes | Exact-artifact/hash approval schema C1; enforcement C3-C4 | Exact hash/revision FK checks; authorization/rejection planned | Authenticated plan/outcome decisions, pending | Governance acceptance, planned | foundation |
| PDF-11, p2 section 4.4 | Bounded retries, fallback, rollback, safe stop | Contracts and recovery schema C1; implementation C4 | Bound/hash contract checks; real recovery planned | Failure, diagnosis, repairs, restored manifest, pending | Repair/safe-stop demos, planned | foundation |
| PDF-12, p2 sections 4.4/4.6 | Security/compliance/change guardrails | Policy schema C1; repository tools C2; patch controls C3; governance C4 | Strict API now; roots/symlinks/hashes/limits planned | Policy decisions and validated proposals, pending | API tests; patch acceptance C3 | foundation |
| PDF-13, p2 section 4.4 | Audit-grade observability and traceability | Atomic submission audit C1; full execution ledger C3-C4; deployment C5 | Atomic rollback and ownership tests; stage ledger tests planned | Intake audit exists; full chain pending | Workflow GET; demos planned | foundation |
| PDF-14, p2 section 4.4 | Success rate, retries/rollbacks, MTTR, end-to-end latency | Baseline actuator C1; workflow instruments C4 | JVM metric check now; reliability metrics planned | Prometheus baseline; workflow measures pending | `/actuator/prometheus` | foundation |
| PDF-15, p2 section 4.5 | Production code, API/schema, unit/integration tests, docs | File proposals/contracts C1; generation C3; hardening C5 | Provenance/immutability now; generated compilation/tests planned | Generated runtime source, discovered tests, docs, pending | Greenfield demo, planned | foundation |
| PDF-16, p2 section 4.6 | Risks, trade-offs, validation, safety | Validation/build contracts C1; tools C3; risk/gates C4 | Stale artifacts/failure classification now; feature gate planned | Validation evidence and risk review, pending | Maven + gate tests | foundation |
| PDF-17, p2 section 4.7 and p3 section 7 | Agents act; humans oversee and own final quality | Strict caller boundary C1; agents C3; approvals C4 | `WorkflowApiTest` rejects completion and evidence | Rejected caller mutations; actual autonomy pending | `scripts/check-foundation.ps1` | foundation |
| PDF-18, p2 section 4.8 | Summary: plan/rationale/artifacts/risks/assumptions/limits | Outcome contracts/storage C1; generation C4 | Incomplete/stale outcome gate tests, planned | Persisted engineering outcome, pending | Outcome query, planned | foundation |
| PDF-19, p2 section 5 | Runnable prototype, architecture, three scenarios, setup/testing/limits | Foundation setup/docs C1; complete prototype C5 | API/H2 suite; real PostgreSQL IT profile; all scenarios planned | Final-image evidence, pending | `mvnw.cmd -Ppostgres-it clean verify`; six demos C5 | foundation |
| PDF-20, pp2-3 sections 6-7 | Modular/testable/reliable/secure/scalable design and defensible judgment | Layered contracts C1; recovery C4; operations C5 | Contract/persistence tests; concurrency/failover planned | Final verification and documented limits, pending | Full verification and failover demo | foundation |

## User-added requirements

| ID | Requirement | Delivery | Current status |
|---|---|---|---|
| USER-01 | Java 21, Spring Boot, Maven Wrapper, PostgreSQL, Flyway, JUnit | C1 | implemented; verification recorded separately |
| USER-02 | No caller-supplied completion or engineering evidence | C1 | implemented; API tests and live check provided |
| USER-03 | Structured CREATE/UPDATE/DELETE, complete content, hashes and criterion/task/input lineage | Contracts C1; guarded tools C3 | contracts only |
| USER-04 | Approved roots, traversal/symlink protection, limits, optimistic hashes, atomic writes, diff/manifest and restoration | C2-C4 | pending |
| USER-05 | Fixed build capability and full process/test/coverage/failure evidence | Schema/contracts C1; runtime C3-C4 | contracts/storage only |
| USER-06 | Ten feature-completion gates including connected behavior and discovered generated tests | C3-C4 | pending |
| USER-07 | Complete URL behavior, analytics, errors, rate limits, URL security, concurrency and cleanup | C3 minimal slice; C5 complete | pending |
| USER-08 | Compose, non-root images, two orchestrators, durable state, restart/failover, Prometheus and CI | C1 database/baseline metrics; C5 complete | partial foundation |
| USER-09 | Numeric coverage enforcement and Java 21 enforcement | C1 Java rule/report; C5 coverage gate | report only; no coverage threshold yet |
| USER-10 | Six API-driven demos showing actual persisted evidence | C4 scenarios; C5 scripts/final images | pending |
| USER-11 | Nine accurate documents and staged approval/manual Git process | Maintained through intelligence/planning | implemented |

## Delivery sequence and approval boundary

1. Foundation, persistence, contracts and strict caller boundary.
2. Requirement intelligence, repository reasoning and dynamic planning.
3. Automatic agent execution and the first real generated/compiled/tested vertical slice.
4. Stateful orchestration, recovery, governance and scenario coverage.
5. Service hardening, deployment, failover, CI and final acceptance.

Requirement intelligence/planning (checkpoint 2) is authorized after user validation of the foundation. Later checkpoints require user validation and
the user-created commit hash. No Git operations are performed by the agent.
Final `git diff --check` is run by the user and returned as evidence.

## Implemented requirement intelligence and planning

| PDF row | Implementation | Automated evidence | Live reviewer command | Status |
|---|---|---|---|---|
| PDF-03 | RequirementInterpreter; authenticated RevisionService | 9 interpreter cases; workflow clarification/authentication tests | check-intelligence.ps1 ambiguous section | implemented; live verification passed |
| PDF-04 / PDF-07 | DynamicPlanner; GraphValidator; persisted tasks/edges/gates | Different criteria/repositories, overlap sequencing, parallel layers, joins, cycles | check-intelligence.ps1 greenfield/brownfield | implemented planning; engineering gate execution pending |
| PDF-05 | RepositoryAnalyzer type/route/reference map | Controller -> service impact and upstream-change checks | check-intelligence.ps1 brownfield section | implemented static reasoning; runtime enhancement pending |
| PDF-06 | Four intelligence Agent roles through ModelProvider/AgentExecutor | ScheduledIntelligenceTest proves automatic stage dispatch | Workflow GET analyses/artifacts/attempts | implemented intelligence; full SDLC pending |
| PDF-09 | Parent revisions, invalidation, exact-manifest repository inventory reuse | Clarification, stale revision, changed source and approval invalidation | check-intelligence.ps1 clarification/replan sections | implemented revision planning; downstream recovery pending |
| PDF-12 | RepositoryTools bounded UTF-8 roots/types/count/size/search/link checks | RepositoryToolsTest; Windows native-link rejection passed | Latest targeted suite | implemented guards; hostile-process OS isolation pending |
| PDF-13 / PDF-17 | Attempt/artifact/validation/audit storage; no completion API | 26 API boundary checks and analysis workflow tests | check-intelligence.ps1, check-foundation.ps1 | implemented analysis lineage; final outcome pending |

Initial matrix entries above identify the original foundation and later delivery
allocation. This table records the subsequent implemented behavior without claiming
that a plan is a completed engineering outcome. Four analysis stages run; production
generation/build/recovery/release/failover are still pending. Latest inspected clean verification
passed 75 default tests and six PostgreSQL integration tests (81 total), with no
failures, errors or skips. Coverage: 901/957 lines (94.15%) and 469/700 branches
(67.00%). Live intelligence verification passed. The reported Maven totals precede the
target-package exclusion fix; clean verification of its additional regression
test remains pending.
