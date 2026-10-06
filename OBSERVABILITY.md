# Observability

Foundation exposes `/actuator/health/liveness`, `/actuator/health/readiness` and
`/actuator/prometheus`. Readiness includes database availability; liveness excludes
the database. Health details are hidden. JVM/process/HTTP/pool metrics are baseline
instrumentation, not evidence that engineering workflows execute successfully.

Submission audit events are persisted atomically with the workflow, revision and
task. Events contain workflow/revision/task IDs, event type, UTC timestamp, a platform
actor and requirement hash. No update/delete audit API exists. The submission actor
identifies the platform action; it is not a verified human identity.

Pending C4: workflow terminal success rate, retries, rollbacks, repair time (MTTR)
and end-to-end latency. Definitions and denominator/terminal-state handling will be
documented with implementation. Use bounded labels; IDs, requirements, URLs and
artifact hashes must remain in evidence storage, not metric labels.

Pending C3-C5: build output truncation, process exit/timeouts, test discovery,
coverage, failure classification, recovery actions, restart/failover audit lineage
and operational evidence. Build stdout/stderr storage is capped at 65,536 characters
per stream by schema; the runner must enforce and mark truncation when implemented.

Audit deployment controls, secret redaction and restricted database privileges
remain required work. Foundation schema access by a database owner is not immutable.
