# Manual acceptance checklist

## Foundation checkpoint

- [ ] Java 21 and pinned Maven Wrapper confirmed.
- [ ] Targeted tests and full Maven verification pass; exact totals recorded.
- [ ] Real PostgreSQL migration/API/constraint profile passes.
- [ ] Database and application start from README commands.
- [ ] Liveness and database-aware readiness respond UP.
- [ ] Strict submission accepts only requirement/repositoryPath and returns 202/Location.
- [ ] GET returns persisted revision, pending task, requirement hash and audit.
- [ ] Completion/evidence injection returns 400 without creating records.
- [ ] Completion endpoint returns 404; PUT/PATCH mutation returns 405.
- [ ] OpenAPI exposes submission/inspection only; Prometheus has baseline metrics.
- [ ] Coverage report inspected; numeric enforcement is explicitly pending.
- [ ] User validates checkpoint and creates the commit manually.

## Final assessment acceptance (pending C3-C5)

- [ ] Requirement-specific plans and connected generated production/tests demonstrated.
- [ ] All ten completion gates satisfied using current-revision evidence.
- [ ] All three PDF scenarios and six requested demos pass against final images.
- [ ] Real failure/repair, rejected approval, verified rollback and replanning inspected.
- [ ] Generated source, tests, diffs, manifests, logs, hashes and outcomes reviewed.
- [ ] Full Maven verification totals and enforced coverage recorded.
- [ ] Two-orchestrator restart/failover evidence reviewed.
- [ ] Final images rebuilt and Compose validated.
- [ ] User-run `git diff --check` passes.
- [ ] All PDF traceability rows verified; optional hardening identified separately.

Unchecked items are not completed claims. Record actual command output and dates
at checkpoint handoff; documentation and unit tests alone cannot satisfy final acceptance.
