# Architecture

## Execution chain

Strict intake -> durable revision -> requirement and ambiguity agents -> clarification
gate -> controlled repository snapshot -> repository analysis -> criterion-specific
DAG -> architecture/security-design agents -> exact CHANGE approval -> production and
test proposals -> synchronization and governed patch application -> fixed Maven clean
verify -> diagnosis/production repair when supported -> post-build documentation/security
review -> ten feature gates -> exact RELEASE approval -> persisted final outcome.

Control-plane records own workflows/revisions/tasks/dependencies/gates, approvals,
policies and audit. Execution-plane interfaces own agents/executors/providers, tools,
artifacts/validators, attempts, retry/fallback and rollback. No caller can complete a
task, supply agent output or choose a shell command. Deterministic providers use typed
capability templates and conservative existing-source transformations; they are not
an arbitrary-domain LLM. No external model is required.

## Scheduling and context

The durable engineering queue is atomically claimed. Ready DAG branches execute on
at most four workers by default, configurable within 1–8. Shared production paths
are serialized by planner dependencies. All test proposals join before mutation.
Tasks receive ancestor artifacts, exact requirement/plan hashes and a persisted task
input. Brownfield later branches see a virtual tree containing their ancestors' exact
proposals, so optimistic UPDATE hashes bind the correct predecessor content. Actual
patch application rechecks those hashes in the same dependency order.

Build/file capabilities execute at exclusive graph synchronization points. Parallel
agent branches generate artifacts only. Documentation/security depend on successful
build evidence; readiness joins both. Evaluated entry, artifact, patch, build and
feature gates are persisted. Conditional diagnosis/repair tasks are appended to the
runtime graph with their approved recovery scope and failed-attempt lineage.

## Recovery and governance

The plan declares at most three build attempts and a production-file repair allowlist.
Real compilation/test failures permit diagnosis; known supported fixes are the missing
brownfield bootstrap type and incorrect generated redirect status. Repair agents return
complete UPDATE content, expected hashes and failure/input lineage. Validators prohibit
repairing tests or build configuration. Unsupported diagnoses, exhausted attempts,
unavailable tools, dependency failures and timeouts restore baseline and safely stop;
there is no blind command retry. Cancellation flags interrupt the child process and
terminate descendants before restoration. Restoration records expected/current manifest
hashes and verification; a restoration failure becomes FAILED, never release-ready.

CHANGE approval binds current revision/plan hash, including recovery scope. RELEASE
approval binds immutable candidate outcome evidence. Release rechecks current files,
artifact hashes, upstream baseline and all gates before recording approval. Rejection
restores the baseline and preserves human rejection evidence. Rollback invalidates
outcomes and approved release decisions. Replanning uses a workflow row lock, parent
revision lineage, evidence/approval invalidation and exact-manifest inventory reuse.

## Files, builds and feature gates

Repository tools enforce approved separate source/workspace roots, bounded UTF-8 files,
traversal/link/junction rejection and verified snapshots. ProposalTool enforces supported
roots/types, 100 operations, 256 KiB per file, 1 MiB per batch, duplicate rejection,
optimistic hashes, atomic file writes, exact content checks, unified diffs and rollback.
Original source and baseline are distinct; source is never mutated by execution.

Fixed Maven clean verify accepts only pinned platform-owned POM/wrapper assets, rejects
Maven extension/config hooks, strips platform secrets and Java/Maven option hooks,
bounds output/time, and parses compiler/class, Surefire and JaCoCo evidence. Skipped
cases do not count. Zero exit without compiled source, tests and coverage cannot pass.

The ten readiness checks cover criterion-to-production, meaningful canonical generated
HTTP tests, compiled changed production, required generated/baseline tests, connected
runtime, successful build, current policies/security validation, current artifact/file
hashes, exact current change approval/upstream snapshot and exact outcome release approval.
A passing candidate has featureComplete=true, releaseReady=false; final approval closes
gate ten. Final outcome links candidate hash, approval, artifacts, attempts and recovery.

## Limits

Four Flyway migrations manage 18 platform tables. Claiming is atomic but crash leases,
fencing and automatic recovery of interrupted claims are stage 5. NIO/file checks are
not an OS sandbox against hostile process races; deterministic generated builds are
trusted local assessment execution. File writes are atomic individually; batches are
restored on handled failure, not crash-atomic. Baseline restoration excludes Maven build
output, which may remain for diagnosis and is never release-authorizing evidence.
Shared-token operators are a local boundary, not independent enterprise identities.
Minimal and legacy fixtures retain in-memory data; complete greenfield generation uses PostgreSQL, Flyway, capability-governed HTTP paths and nine generated HTTP tests. Native PostgreSQL tests establish transactional counters and alias contention. Deployment acceptance is recorded separately.

## Complete URL capability generation

FullShortenerSources reads only original platform-owned source. A complete greenfield plan creates eleven Java production files, pinned build assets and Flyway/configuration resources. Subsequent criterion tasks update the shared UrlCapabilities gate, so they serialize on optimistic predecessor hashes. Each capability has an independently generated HTTP test; testing creates deterministic DNS/H2 support. Policy validation permits only the declared Java/configuration/migration paths. Full and minimal POMs are exact platform-controlled capabilities, never agent command text. Root and generated JaCoCo checks fail verification below documented numeric thresholds.
