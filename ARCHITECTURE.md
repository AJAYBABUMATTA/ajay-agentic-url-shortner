# Architecture

## Automatic intelligence pipeline

Strict API -> durable RECEIVED revision -> scheduled atomic intake claim ->
requirement agent -> ambiguity agent -> clarification gate -> controlled repository
snapshot -> repository agent -> dynamic planning agent -> change approval gate.

The worker invokes Agent/ModelProvider contracts through IntelligenceExecutor.
Each analysis stage records a running attempt, typed artifact, schema/lineage
validation, terminal attempt and audit record. IntelligenceExecutor rejects
engineering roles. Production/test generation and controlled application remain
pending. Provider output cannot execute commands or mutate a target.

The intake worker polls persisted RECEIVED records, so unclaimed intake survives
restart. Recovery of a crash after a claim, worker leases/fencing, retries and parallel
execution are later work. Planned parallel branches exist now; no claim is made
that production branches execute concurrently yet.

## Control and execution planes

Workflow/revision/task/dependency/gate/policy/approval/audit records govern lifecycle.
Agents, providers, controlled tools, artifacts, validators, attempts and workspace
contracts define the execution boundary. The analysis executor currently performs
four specialist roles. Subsequent engineering execution must produce code/tests,
run fixed builds and satisfy feature-level gates before completion.

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
verification. Production identity/roles and high-impact approvals remain later work.

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

PostgreSQL/Flyway owns 16 platform tables. JDBC transactions keep intake/revisions
atomic. Composite FKs bind evidence to revisions and exact hashes. UTC timestamps
use microsecond precision. JSON/text payloads are application-validated and hashed;
database owners can still alter them. Audit has no modification API.

NIO checks detect links and changes but are not an OS sandbox against a hostile
process racing ancestor replacement. Production build workers need restricted
filesystem/process credentials, isolated mounts and no platform secrets. Fixed
Maven commands still execute repository build logic. Build sandboxing precedes
engineering process execution. Final service hardening and failover are pending.
