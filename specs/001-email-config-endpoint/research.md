# Phase 0 Research - Recipient & Prompt Configuration API

**Date**: 2025-11-23  
**Branch**: `001-email-config-endpoint`  
**Source Spec**: [spec.md](./spec.md)

Each task resolves a NEEDS CLARIFICATION item from the plan's Technical Context and documents the decision, rationale, and alternatives.

## R1 - Firestore Optimistic Locking & Prompt Hashing
- **Decision**: Use Firestore transactions with an explicit `version` field and `precondition.lastUpdateTime` guard. Every successful update increments `version`, stores `updatedAt` with the server timestamp, and recomputes `promptSha256` (SHA-256 over normalized prompt text). Transactions roll back automatically if the stored `version` or `updateTime` changes between read and write.
- **Rationale**: Transactions keep PUT idempotent even when concurrent admins submit changes within milliseconds. Explicit versioning simplifies API conflict responses (HTTP 409 with expected/current versions) and aligns with SC-001/SC-002 latency goals because Firestore transactions stay sub-100 ms for single-document writes. Hashing prevents raw prompt exposure in logs or metrics while enabling clients to detect content drift via `ETag`.
- **Alternatives considered**:
  1. **Blind overwrite using `set(..., merge=true)`** - rejected because it loses audit accuracy and cannot detect stale clients; conflicted updates would silently overwrite each other.
  2. **Rely solely on `updateTime` preconditions** - rejected because the API must still expose a human-readable `version` string; combining both adds clarity with negligible cost.
  3. **Encrypt prompt text before storage** - unnecessary for this feature because prompts are already sanitized, and encryption would complicate Terraform seeding plus caching without delivering additional value today.

## R2 - `updatedBy` Derivation & Audit Metadata
- **Decision**: Extend the existing API key filter to attach an `ApiKeyMetadata` object (owner slug, environment, hashed key ID) to the request attributes. Controllers read this metadata to fill `updatedBy` in Firestore and `actorHash` in logs. If metadata is missing, the controller stores `system:unknown` and still blocks unauthorized calls with 401, ensuring audit completeness without leaking secrets.
- **Rationale**: Leveraging middleware avoids duplicating authentication logic in controllers and keeps the audit story consistent with other endpoints. Hashing key IDs before logging means compliance reviewers can correlate activity without storing raw secrets. This approach also scales when multiple API keys map to the same team.
- **Alternatives considered**:
  1. **Require callers to pass an `updatedBy` field** - rejected because malicious clients could spoof ownership, undermining audit trust.
  2. **Look up key metadata inside the controller via Secret Manager** - rejected for latency and cost; the middleware already resolves metadata once per request.
  3. **Use Cloud Run service account identity** - rejected because multiple admins can share the same service account, preventing per-key accountability.

## R3 - Metrics & Alerting Standards for Configuration Endpoints
- **Decision**: Publish three Micrometer counters (`config.update.success`, `config.update.failure`, `config.update.unauthorized`) with labels `(env, actorHash, sourceIp, outcome)` plus a histogram `config.update.duration` (buckets: 50ms, 100ms, 250ms, 500ms, 1s). Cloud Monitoring alert policies trigger when unauthorized failures exceed 5/minute for 5 consecutive minutes or validation failures exceed 3/minute for 3 minutes. Structured logs capture the same correlation ID used by alerts.
- **Rationale**: Separate counters keep dashboards clear and align with existing naming used by Ops. Duration histograms satisfy SC-001 latency evidence. Alert thresholds match the spec's chaos-test expectations (>=5 unauthorized attempts -> incident). Shared correlation IDs make it easy to pivot from alerts to logs.
- **Alternatives considered**:
  1. **Single counter with `status` label** - rejected because it complicates alert expressions and obscures the reason for failure in Grafana/Cloud Monitoring panels.
  2. **Tracing-only observability** - rejected because metrics + logs provide faster detection and are mandated by the constitution.
  3. **Lower thresholds (e.g., 1/min)** - rejected to avoid noisy alerts during manual testing; chosen values still detect abuse promptly without paging on expected validation errors during development.
