# Engineering outcomes

Current workflows produce reviewable requirement, ambiguity, repository inventory
and dynamic plan artifacts with persisted attempt/hash lineage. After exact plan
approval, a supported greenfield create/redirect workflow generates source/tests,
applies them in its isolated workspace and runs Maven verification. A passing slice
persists VERIFIED_VERTICAL_SLICE_RELEASE_GATED with releaseReady=false and enters
AWAITING_RELEASE_APPROVAL. No release approval endpoint is exposed in this stage.

Clarification creates a child revision. Requirement-dependent evidence/approvals are
invalidated; unchanged repository inventory is reused only after current manifest
verification. Inspection exposes original/current revision links and reuse IDs.

The current bounded outcome contains generated production/test criterion mappings,
compiled paths, executed/failed test cases, coverage, plan/current manifest hashes
and artifact hashes. Companion artifacts contain architecture rationale, exact file
operations, manifests/diffs, build logs, documentation/security limitations and input
lineage. Build failures persist evidence and stop without a successful outcome.
The complete final outcome still needs diagnosis/repair/recovery evidence, complete
feature/security gates and authenticated exact-outcome release approval.

Every behavioral criterion must connect to actual runtime production behavior and
meaningful generated tests. Passing unrelated tests, schemas, narrative output or
zero exit codes without feature evidence cannot establish readiness. Supported
planning capabilities do not imply implemented generation/repair capabilities.
