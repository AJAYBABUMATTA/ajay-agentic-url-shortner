# Manual acceptance

## Requirement intelligence and planning

- [x] Default clean verification completed: 75 passed, zero failures/errors/skips.
- [x] Automatic scheduled dispatch tested without a user stage command.
- [x] Clear/ambiguous requirements and missing/conflicting policies tested.
- [x] Authentication, expected-revision conflicts and invalid answer IDs tested.
- [x] Distinct requirements/repositories produce different task graphs/impacts.
- [x] Ambiguity causes no workspace creation or source mutation.
- [x] Verified snapshots preserve source/baseline and reject unsafe/oversized inputs.
- [x] Requirement replanning invalidates evidence/approvals and verifies inventory reuse.
- [x] Upstream source changes prevent inventory reuse in automated tests.
- [x] Updated native-link rejection test passes on Windows.
- [x] Six real PostgreSQL checks pass with the latest source (81 total tests).
- [x] Live check-intelligence.ps1 passes against restarted latest application.
- [ ] Current targeted tests and final verification results recorded.
- [ ] User validates this delivery and creates the commit manually.

## Final assessment acceptance

- [ ] First complete generated, compiled and tested URL-shortener slice.
- [ ] Every behavioral criterion maps to connected production and meaningful tests.
- [ ] Failure-driven repair, bounded retry/fallback, verified rollback and safe stop.
- [ ] Authenticated exact plan/outcome approval, rejection and invalidation.
- [ ] Reliability metrics and end-to-end lineage verified.
- [ ] Complete service API, security, analytics, rate limiting and concurrency checks.
- [ ] Non-root images, two orchestrators, durable recovery and failover proven.
- [ ] Coverage threshold enforced; final Maven totals recorded.
- [ ] Images rebuilt, Compose validated, six demos run against final images.
- [ ] Generated source/tests/diffs/manifests/logs/hashes/outcomes inspected.
- [ ] User-run git diff --check and all PDF traceability rows verified.

Documentation or passing platform tests alone do not satisfy engineering acceptance.
