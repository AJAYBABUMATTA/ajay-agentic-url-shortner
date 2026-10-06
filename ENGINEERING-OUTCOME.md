# Engineering outcomes

Current workflows produce reviewable requirement, ambiguity, repository inventory
and dynamic plan artifacts with persisted attempt/hash lineage. They stop at
clarification or change approval. These are planning outcomes, not completed
engineering outcomes. No workflow can generate source or claim release readiness yet.

Clarification creates a child revision. Requirement-dependent evidence/approvals are
invalidated; unchanged repository inventory is reused only after current manifest
verification. Inspection exposes original/current revision links and reuse IDs.

The final outcome must additionally contain architecture rationale, structured
production/test proposals, exact applied contents, manifests/diffs, real compiled
source and discovered/executed tests, failure/repair/recovery evidence, security and
policy decisions, authenticated exact approvals, risks, assumptions and limitations.

Every behavioral criterion must connect to actual runtime production behavior and
meaningful generated tests. Passing unrelated tests, schemas, narrative output or
zero exit codes without feature evidence cannot establish readiness. Supported
planning capabilities do not imply implemented generation/repair capabilities.
