# Assignment traceability

Source: all three pages of Assignment Agentic-Proficient Software Engineer (1).pdf.
The PDF defines requirements; the user request controls development and adds the
stack, safeguards and five-stage approval/manual-Git process. Reference projects
were inspected for patterns only; code, artifacts, credentials and history were not copied.

Implemented below means verified within documented deterministic prototype capabilities;
it does not claim arbitrary-domain engineering, production security or final deployment.
Full verification counts and coverage belong in REVIEWER-GUIDE.md.

| ID / PDF source | Requirement | Implementation | Meaningful tests | Runtime evidence | Reviewer command | Status |
|---|---|---|---|---|---|---|
| PDF-01 p1 §1 | Requirement to reviewable engineering outcome | Intelligence/execution chain; FeatureCompletionValidator; ReleaseApprovalService | GeneratedApiIT; RecoveryGovernanceIT | Real generated source/tests/build and exact approved final outcome | demo.ps1 greenfield; engineering GET | implemented bounded prototype |
| PDF-02 p1 §3 | Greenfield/brownfield/test/docs/clear/ambiguous | Original scenario fixtures and specialized providers | GeneratedSliceIT; RecoveryGovernanceIT | Connected analytics, ambiguity child revision, docs/security artifacts | demos greenfield/brownfield/ambiguous | implemented bounded scenarios |
| PDF-03 p1 §4.1 | Interpret/normalize intent and ambiguity | RequirementInterpreter; RevisionService | RequirementInterpreterTest; IntelligenceWorkflowTest | Criteria/questions/assumptions/risks; no workspace before resolution | check-intelligence.ps1; demo ambiguous | implemented |
| PDF-04 p1 §4.2 | Dependency-aware decomposition | DynamicPlanner; GraphValidator | DynamicPlannerTest | Distinct criterion/repository DAGs and layers | workflow GET; planner tests | implemented |
| PDF-05 p1 §4.3 | Brownfield module/API/data-flow reasoning | RepositoryAnalyzer; BrownfieldSources conservative runtime edits | RecoveryGovernanceIT | Actual UrlController -> UrlService diff; six HTTP + baseline unit cases | demo brownfield | implemented supported fixture |
| PDF-06 pp1–2 §4.4 | Stateful non-linear full SDLC | AgentExecutor; ready-branch scheduler; conditional recovery graph | GeneratedApiIT; RecoveryGovernanceIT | Automatic attempts, diagnosis/repair/reverification, outcome | demos repair/greenfield | implemented; lease recovery tested, six final-image demos passed |
| PDF-07 pp1–2 §4.4 | Explicit graph, entry/exit gates | Persisted dependencies/execution_gates; validators | Graph/cycle tests; exact gate acceptance | Evaluated entry/artifact/patch/build/feature gates | workflow/engineering GET | implemented |
| PDF-08 p2 §4.4 | Sequential/parallel/synchronization | Bounded pool; overlap dependencies; proposal join | RecoveryGovernanceIT two-branch latch; brownfield UPDATE hashes | Concurrent branch attempts; sequential shared-file proposals | demo brownfield; IT suite | implemented |
| PDF-09 p2 §4.4 | Context/decision lineage/replanning | Ancestor inputs; virtual tree; revisions/invalidation/reuse | IntelligenceWorkflowTest; upstream-change recovery IT | Input hashes, parent lineage, fresh plan and rejected old evidence | check-intelligence.ps1; replan API | implemented |
| PDF-10 p2 §4.4 | Human approval of high-impact changes | Exact plan and immutable outcome approval | EngineeringWorkflowTest; GeneratedApiIT; rejection/rollback IT | Authenticated hash decisions, stale 409 and invalidation | demo exact hash continuations | implemented local token identity |
| PDF-11 p2 §4.4 | Bounded retry/fallback/rollback/safe stop | RecoveryPolicy; diagnosis/repair agents; BaselineRollbackTool | RecoveryPolicyTest; genuine failure/three-attempt/cancel IT | Failed builds, repaired production, recovery ledger, restored hashes | demos repair/safe-stop | implemented supported fixes |
| PDF-12 p2 §§4.4/4.6 | Security/compliance/change guards | Safe roots; ProposalTool; lineage validators; fixed Maven; security policies | RepositoryToolsTest; ProposalToolTest; governance tests | Exact validated operations, policy verdicts, filtered environment | tests; engineering GET | implemented; non-root image defined, six final-image demos passed |
| PDF-13 p2 §4.4 | Audit/traceability | Durable attempts/artifacts/policies/approvals/audit | PostgresFoundationIT; GeneratedApiIT | Complete requirement/criterion/task/artifact/build/outcome hashes | workflow/engineering GET | implemented; interrupted-attempt recovery tested |
| PDF-14 p2 §4.4 | Success/retry/rollback/MTTR/latency metrics | EngineeringMetrics counters/timers; durable release gauge | Recovery flows and metrics inspection | Prometheus workflow/agent/recovery durations and outcome counts | /actuator/prometheus | implemented process counters; gauge durable |
| PDF-15 p2 §4.5 | Production/API/tests/docs artifacts | Deterministic generation; actual brownfield edits; specialists | GeneratedSliceIT; RecoveryGovernanceIT | Compiled runtime, discovered meaningful HTTP/regression tests, docs | demos; engineering GET | implemented full PostgreSQL greenfield and bounded brownfield |
| PDF-16 p2 §4.6 | Risk/validation/safety/tradeoffs | Ten gates; evidence-driven diagnosis; explicit limits | Genuine failure, stale hash/upstream drift and rollback IT | Gate results, failed evidence, risks/limits | full verify; docs | implemented prototype scope |
| PDF-17 p2 §4.7/p3 §7 | Agents act; humans oversee final quality | Automatic providers/tools; strict caller boundary | WorkflowApiTest; GeneratedApiIT | No completion/evidence endpoint; exact human decisions | check-foundation.ps1; demos | implemented |
| PDF-18 p2 §4.8 | Final plan/rationale/artifacts/risks/assumptions/limits | SliceOutcome and companion artifacts | Exact final outcome ten-gate IT | Final approved traceability, attempts/recovery/approvals | engineering GET | implemented |
| PDF-19 p2 §5 | Runnable prototype/architecture/three scenarios/setup/tests | Maven local app; six API demo scenarios; nine docs | Full Maven/PG/generated-build suite | Local API and retained test exports | README commands | implemented; six final-image demos passed |
| PDF-20 pp2–3 §§6–7 | Modular/testable/reliable/secure/scalable judgment | Layered contracts, bounded parallelism/recovery, documented limits | Full suite; PostgreSQL concurrency and final-container failover verified | Current stage evidence; final-container failover evidence exported | full verify; final failover demo | leases/recovery and PostgreSQL concurrency tested; six final-image demos passed |

## User additions and remaining acceptance

| Requirement | Current implementation/status |
|---|---|
| Java 21/Boot/Wrapper/PG/Flyway/JUnit/OpenAPI/RFC errors | implemented platform foundation; six migrations, 22 tables |
| No caller completion or supplied output | strict API and automatic executors; tested |
| Full structured CREATE/UPDATE/DELETE lineage | FileOperation/EngineeringValidator/ProposalTool; tested |
| Roots/traversal/links/count/size/hashes/atomic writes/diffs/restoration | implemented; handled-failure restoration; not crash-atomic batch |
| Fixed commands, actual compilation/discovered tests/coverage/failure evidence | implemented real Maven clean verify |
| Ten feature gates and exact current approvals | implemented; candidate false until gate ten approved |
| Complete URL APIs/persistence/security/rate limits/concurrency/cleanup | complete PostgreSQL APIs plus minimal/legacy target variants; security and concurrency tested |
| Images/two workers/durable recovery/failover/CI | two non-root workers/shared workspaces and CI defined; six final-image demos passed |
| Java enforcement and numeric coverage threshold | Java 21 enforced; root 85% lines/65% branches and generated 80%/50% enforced in verify |
| Six final-image API demos | six API scenarios implemented; all six final-image scenarios passed |
| Nine accurate documents and staged manual commits | maintained; no agent Git operations |

Delivery stages 1–3 were validated with user commit hashes. Stage 4 covers stateful
orchestration/recovery/governance/scenarios; stage 5 begins only after user validation
and a manual commit hash. Final git diff --check is run by the user. No checkpoint
evidence document is required; runtime evidence is exposed through APIs/reports.
Stage 5: FullGeneratedApiIT proves eleven generated production classes, nine generated
HTTP tests, real build/coverage and all ten exact-approval gates. UrlApiIT verifies real
PostgreSQL aliases, expiry, ownership, cleanup and concurrent counters. UrlSecurityTest
covers unsafe addressing and mixed/failed DNS. UrlRateLimiterTest proves concurrent
shared quota and forwarding-header rejection. WorkerRecoveryTest verifies lock fencing
and unknown interrupted-build safe stop. Docker/Compose/CI and the acceptance harness
must be executed against the final image before final runtime rows are marked accepted.
Final full verification: 164 tests, zero failures/errors/skips; line coverage 95.69%, branch coverage 74.74%. All six Docker API scenarios passed. All 91 exported artifact hashes verified. User reported final-container URL retention passed; user Git whitespace recheck remains pending.
