# Manual acceptance

## Requirement intelligence and planning

- [x] Default clean verification completed: 90 passed, zero failures/errors/skips.
- [x] Automatic scheduled dispatch tested without a user stage command.
- [x] Clear/ambiguous requirements and missing/conflicting policies tested.
- [x] Authentication, expected-revision conflicts and invalid answer IDs tested.
- [x] Distinct requirements/repositories produce different task graphs/impacts.
- [x] Ambiguity causes no workspace creation or source mutation.
- [x] Verified snapshots preserve source/baseline and reject unsafe/oversized inputs.
- [x] Requirement replanning invalidates evidence/approvals and verifies inventory reuse.
- [x] Upstream source changes prevent inventory reuse in automated tests.
- [x] Updated native-link rejection test passes on Windows.
- [x] Six PostgreSQL checks plus four generated-build/API checks pass (100 total tests).
- [x] Live check-intelligence.ps1 passes against restarted latest application.
- [x] Engineering suites and full clean verification results recorded.
- [ ] User validates this delivery and creates the commit manually.

## Final assessment acceptance

- [x] First complete generated, compiled and tested create/redirect URL-shortener slice.
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

## Engineering execution stage

- [x] Exact current-plan authentication and approval queue execution automatically.
- [x] Original fixture stays unchanged; generated files live in revision workspaces.
- [x] Agents produce full file operations with optimistic hashes and criterion/task lineage.
- [x] Validators reject invalid scope, paths, duplicates, limits and stale hashes.
- [x] Applied contents are verified against proposals; manifest and unified diff are persisted.
- [x] Failed partial patch batches restore prior contents in automated tests.
- [x] Fixed Maven tool accepts only platform build assets and strips platform secrets.
- [x] Real generated HTTP tests verify create, 302 redirect, invalid input and missing code.
- [x] Real compiler and HTTP-test failures persist evidence and safely stop.
- [x] Latest HTTP-only PostgreSQL execution and requested 301 scenario verified.
- [x] Final clean verification: 100 tests; 95.00% line / 71.48% branch coverage.
- [ ] User validates the persistent greenfield API demo and provides a manual commit hash.

Stage 4 still owns automatic diagnosis/repair, parallel scheduling, recovery policies,
durable rollback, cancellation, complete feature gates and release approval. Stage 5
owns complete URL-service hardening, final images/CI/coverage enforcement/failover.
