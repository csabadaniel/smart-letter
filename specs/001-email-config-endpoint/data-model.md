# Data Model – Recipient & Prompt Configuration API

**Date**: 2025-11-23  
**Branch**: `001-email-config-endpoint`

## Entities

### DeliveryConfiguration (Firestore document: `appSettings/configuration/delivery`)
| Field | Type | Source | Validation / Notes |
|-------|------|--------|---------------------|
| `recipientEmail` | string | PUT request body | Required, trimmed, RFC 5322 compliant, <=254 chars, lowercase stored for idempotency. |
| `llmPrompt` | string | PUT request body | Required, trimmed, printable ASCII only, length 1-4000, sanitized to remove secrets/control chars before persistence. |
| `promptSha256` | string | Service generated | Hex-encoded SHA-256 of normalized prompt for logging/ETag. Never null. |
| `version` | integer | Service generated | Starts at 1, increments atomically per successful write; used for optimistic locking + API responses. |
| `updatedBy` | string | API key metadata | Format `teamSlug:env`; defaults to `system:unknown` if metadata absent. |
| `updatedAt` | timestamp | Firestore server timestamp | Set by Firestore transaction using `FieldValue.serverTimestamp()`. |
| `ttlSeconds` | integer | Derived config | Optional future proofing; currently fixed at 60 to mirror cache TTL. |

### ConfigurationAuditEvent (structured log entry)
| Field | Type | Population | Notes |
|-------|------|------------|-------|
| `eventId` | UUID | Generated per request | Correlates with HTTP trace + metrics labels. |
| `actorHash` | string | Hash of API key ID | Enables investigations without exposing key. |
| `action` | enum | `UPSERT` or `READ` | Aligns with Micrometer labels. |
| `outcome` | enum | `SUCCESS`, `VALIDATION_ERROR`, `CONFLICT`, `UNAUTHORIZED`, `INFRA_ERROR` | Mirrors HTTP status. |
| `configVersion` | integer | From DeliveryConfiguration | `null` for failures that do not reach Firestore. |
| `promptHash` | string | `promptSha256` | Allows diffing without raw prompt text. |
| `timestamp` | instant | System clock | Used for compliance exports. |

### DeliveryConfigurationCache (in-memory)
| Field | Type | Notes |
|-------|------|-------|
| `value` | DeliveryConfiguration | Cached document including metadata. |
| `etag` | string | Derived from `version` + `promptSha256`. |
| `expiresAt` | instant | `retrievedAt + 60s`; cache invalidates earlier on successful PUT. |

### ApiKeyMetadata (middleware context)
| Field | Type | Notes |
|-------|------|-------|
| `keyIdHash` | string | SHA-256 of API key identifier for logging. |
| `ownerSlug` | string | Human-readable owner/team label. |
| `environment` | enum (`test`, `prod`) | Bound when Secret Manager injects keys. |
| `rateLimitBucket` | string | Used by middleware; referenced in metrics for debugging throttling. |

## Relationships & Flows

1. `DeliveryConfigurationController.putConfig` → validates request → delegates to `DeliveryConfigurationService.upsert`.
2. Service loads `ApiKeyMetadata` from request attributes, enters Firestore transaction (via `DeliveryConfigurationRepository`), verifies `version`/`updateTime`, updates document, recomputes `promptSha256`, and invalidates DeliveryConfigurationCache.
3. Successful write emits `ConfigurationAuditEvent`, increments `config.update.success`, and returns DTO with `version`, `updatedAt`, `updatedBy`, and response headers `ETag` (derived from `promptSha256` + `version`) and `Last-Modified` (`updatedAt`).
4. `GET /v1/config/delivery` first checks DeliveryConfigurationCache. On cache hit, returns cached DTO with headers and logs a read audit event. On miss, reads Firestore once, refreshes cache, or returns 404 if the document is absent.
5. Downstream request -> LLM -> email flows (future story) resolve configuration exclusively through DeliveryConfigurationService, ensuring a single source of truth.

## Validation & State Transitions

- **Initial State**: IaC seeds the document with `status=pending` placeholder or deletes collection entirely. First PUT must pass validation; service creates document with `version=1`.
- **Update State**: Each successful PUT increments `version` and updates timestamps. If the provided `version` header (optional) does not match Firestore, service throws `ConfigVersionConflictException` → HTTP 409.
- **Missing State**: GET returns 404 with error code `CONFIG_NOT_FOUND` if document is absent. This doubles as a deployment gate for downstream pipelines.
- **Error Handling**: Validation failures never touch Firestore and log sanitized fields. Firestore 5xx errors bubble up as HTTP 503 with `Retry-After: 30` and emit `config.update.failure{reason=infra}`.

## Firestore & Cache Considerations

- Document stays under 10 KB even with 4k prompt limit; Always Free write quota respected (<100 writes/day as per spec SC-006).
- Repository enforces deterministic field ordering and uses `FieldValue.increment(1)` for `version` inside the transaction.
- Cache TTL (60s) is configurable through Spring properties and mirrored in `DeliveryConfigurationCache` plus response `Cache-Control: private, max-age=60` for GET requests.
- Integration tests spin up the Firestore emulator via Testcontainers, seed fixture data using JSON import, and clean up after each suite to avoid crosstalk.
