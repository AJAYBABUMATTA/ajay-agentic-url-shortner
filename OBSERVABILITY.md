# Observability

Database-aware readiness, independent liveness and JVM/process/HTTP/pool metrics are
available through Actuator. GET workflow and engineering expose persisted attempts,
artifacts/input hashes, evaluated gates, policies, approvals, recovery and rollback.
Audit records cover dispatch, validated output, patch/build evidence, state transitions,
revision changes, exact human decisions, invalidation and verified baseline restoration.

| Micrometer metric | Meaning |
|---|---|
| agentic.release.success.rate | Durable current RELEASE_READY workflows / terminal workflows; zero when no terminal workflows |
| agentic.workflows.outcomes | Terminal release/stop/rollback/cancellation transitions; bounded outcome label |
| agentic.workflow.duration | Submission-to-terminal-transition duration; bounded outcome label |
| agentic.agent.duration | Agent/tool attempt duration; fixed role labels |
| agentic.retries | Repair-backed build retries, bounded failure classification |
| agentic.fallbacks | Unsupported/exhausted recovery decisions, bounded action |
| agentic.rollbacks | Restoration attempts, verified=true/false |
| agentic.recovery.duration | First failed build start to subsequent successful verification; histogram/timer mean supports MTTR inspection |

Prometheus exports these with underscore names and timer _seconds suffixes through
/actuator/prometheus. Timers/counters are process-local and reset on restart; the
release success gauge queries durable workflow state. Transition counts can include
later rollback of an earlier approved workflow, so they differ from unique-workflow
counts. Analysis/build success alone is never release success. IDs/hashes live in the
ledger, not unbounded metric labels.

Build evidence persists exit, duration, timeout, bounded stdout/stderr, compiled paths,
discovered/failed tests, coverage and classification. Each stream retains a 60,000-byte
prefix while draining the remainder. Environment secrets and Java/Maven option hooks
are excluded from child processes. Error responses omit SQL/path/parser internals.
Read APIs/source evidence and metrics need production access controls before deployment.
Audit is append-only through the API, not tamper-proof against database owners.