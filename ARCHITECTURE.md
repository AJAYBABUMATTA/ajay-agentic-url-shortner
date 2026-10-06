# Architecture

## Automatic intelligence pipeline

Strict API -> durable RECEIVED revision -> scheduled atomic intake claim ->
requirement agent -> ambiguity agent -> clarification gate -> controlled repository
snapshot -> repository agent -> dynamic planning agent -> change approval gate.

The worker invokes Agent/ModelProvider contracts through IntelligenceExecutor.
Each analysis stage records a running attempt, typed artifact, schema/lineage
validation, terminal attempt and audit record. IntelligenceExecutor rejects
engineering roles. A separate EngineeringExecutor now dispatches architecture,
implementation, testing, documentation and security specialists, and controlled
patch/build tools. Provider output cannot execute commands or mutate a target.

The intake worker polls persisted RECEIVED records, so unclaimed intake survives
restart. Recovery of a crash after a claim, worker leases/fencing, retries and parallel
execution are later work. Planned parallel branches exist now; no claim is made
that production branches execute concurrently yet.

## Control and execution planes

Workflow/revision/task/dependency/gate/policy/approval/audit records govern lifecycle.
Agents, providers, controlled tools, artifacts, validators, attempts and workspace
contracts define the execution boundary. The analysis executor currently performs
four analysis roles. Engineering agents return structured CREATE/UPDATE/DELETE
proposals with complete content, optimistic hashes, task/criterion/revision lineage
and input hashes. Their validators enforce the exact approved task file scope.

## Requirement and revision semantics

Offline interpretation extracts observable criteria, assumptions, risks and
constraints. Missing scope/policies/units, unmeasurable performance and conflicting
redirect/analytics requirements produce questions. Answers must match the outstanding
question IDs and actually resolve ambiguity; a non-answer can create another paused
revision without source access.

Clarification/replanning use authenticated shared-token operators and expected
revision numbers. A workflow row lock serializes revision creation. Parent lineage
is preserved. Requirement-derived artifacts, pending tasks and prior approvals are
invalidated; older rows remain available for audit. Repository inventory may be copied
into the new revision only after current source manifests match; the reuse ledger
links original/current artifacts. Requirement-specific plans are always regenerated.

Shared-token authorization is a local assessment boundary, not independent identity
verification. Exact-plan CHANGE approval now governs engineering dispatch; production
identity/roles and exact-outcome RELEASE approval remain later work.

## Repository and planning

RepositoryTools validates relative selectors and approved roots, rejects traversal,
symlinks/junction changes, unsupported files and excessive counts/sizes, excludes
build/cache/secret directories, and reads strict UTF-8 through non-following channels.
Source, baseline and workspace contents are verified after copying. Baseline and
repository locations are separate. No target-source write is performed.

Limits default to 2,000 files, 256 KiB per file and 10 MiB total. Literal search is
bounded to 100 results. Unavailable or unsafe repositories stop the workflow safely.
Partial snapshots after failure are not trusted or resumed in this stage.

Static analysis finds Java types, Spring routes and source type references. These
are impact/data-flow candidates; actual runtime connectivity requires generated
compilation and integration tests. Brownfield impacts conservatively include runtime
controllers/services/domain/persistence candidates to avoid omitting dependencies.

Planning creates implementation/testing branches per behavioral criterion, varies
paths with repository structure, serializes overlapping production impacts, and
adds synchronization, architecture/security and documentation/release joins.
Graph validation rejects duplicate/missing/self dependencies and cycles. Gates and
layers are persisted; pending engineering tasks cannot be manually completed.

## Persistence and limitations

PostgreSQL/Flyway owns 17 platform tables. JDBC transactions keep intake/revisions
atomic. Composite FKs bind evidence to revisions and exact hashes. UTC timestamps
use microsecond precision. JSON/text payloads are application-validated and hashed;
database owners can still alter them. Audit has no modification API.

NIO checks detect links and changes but are not an OS sandbox against a hostile
process racing ancestor replacement. Production build workers need restricted
filesystem/process credentials, isolated mounts and no platform secrets. Fixed
Maven commands execute build logic. The bounded greenfield runner accepts only the
platform-owned pinned POM/wrapper, rejects Maven extension/configuration hooks and
strips platform secrets and Java/Maven option hooks from the child environment.
This reduces the execution surface but is not OS process isolation. Final service
hardening, restricted worker containers and failover are pending.

## First engineering execution slice

Exact current plan approval atomically queues engineering_runs. A single-instance
poller claims the run and dispatches pending tasks in dependency-valid sequential
order. Stage/task input artifacts are persisted before execution. Proposals remain
unapplied until all generated test branches finish. ProposalTool rejects duplicate
paths, traversal/links, unsupported roots/types, oversized operations and optimistic
hash conflicts. It writes each file atomically, verifies exact final contents and
restores prior contents after a failed batch; it does not provide crash-atomic batch
application or durable whole-workflow rollback yet.

MavenBuildTool runs fixed Maven Wrapper clean verify with a bounded timeout, drains
both output pipes while retaining bounded prefixes, and reads compiler, Surefire and
JaCoCo reports with external XML resolution disabled. Compiled paths require class
outputs; skipped tests do not count as executed. A zero exit without compiled source,
executed tests and coverage cannot pass. Before and after build/outcome validation,
manifest checks prevent unnoticed workspace drift.

The bounded outcome binds criteria to generated production paths and executed HTTP
tests. Its releaseReady flag is always false in this stage. release-readiness remains
AWAITING_APPROVAL with its RELEASE_APPROVED exit gate closed. Unsupported capabilities
and failed builds stop safely with evidence; parallelism, diagnosis/repair, bounded
retries/fallback, durable rollback, cancellation and release governance remain next.
