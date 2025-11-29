# Implementation Plan: Recipient & Prompt Configuration API

**Branch**: `001-email-config-endpoint` | **Date**: 2025-11-23 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `specs/001-email-config-endpoint/spec.md`

## Summary

Expose authenticated `PUT /v1/config/delivery` and `GET /v1/config/delivery` endpoints that allow Smart Letter ops to persist exactly one recipient email address and one deterministic LLM prompt in Firestore. The controller layer remains the OpenAPI source of truth, so Springdoc regeneration will update `docs/contracts/openapi.yaml`. Writes validate RFC 5322 email syntax, enforce printable ASCII prompts (1-4000 chars), capture audit metadata (`version`, `updatedBy`, `updatedAt`, `promptSha256`), and emit Micrometer events plus structured logs. Reads serve the cached Firestore record with cache headers so downstream flows can gate request -> LLM -> email processing on a known configuration. DeliveryConfigurationService centralizes Firestore access, caching (<=60s TTL), and optimistic locking to keep the Always Free quota safe while making concurrent updates predictable. Observability (metrics, alerts, logs), IaC updates, and Swagger UI exposure ensure the feature satisfies constitution guardrails before later stories invoke the stored prompt.

## Technical Context

- **Language & Framework**: Java 21 with Spring Boot 3.3.x, Spring MVC controllers (`DeliveryConfigurationController`), and component-scanned services/repositories under `com.smartletter.settings`.
- **Dependencies**: Spring Web, Spring Validation, Spring Security API key middleware, Spring Cloud GCP Firestore starter, Micrometer, Springdoc OpenAPI, Cucumber, Testcontainers (Firestore emulator), and Maven Wrapper for builds/tests.
- **Scaffolding & Starters**: The service was originally generated via Spring Boot CLI (`spring init --dependencies=web,validation,data-firestore`) and any new module wiring for this feature (controllers, services, config) must continue to rely on official Spring Boot Starters per Constitution v2.1.1 Runtime Stack guardrail; no ad-hoc manual bootstrapping is allowed.
- **Interfaces**: New DTOs `DeliveryConfigurationRequest`, `DeliveryConfigurationResponse`, and `ErrorResponse` align with annotated controller methods so `./mvnw springdoc-openapi:generate` outputs the contract consumed by Swagger UI.
- **Storage**: Single Firestore document `appSettings/configuration/delivery` holds the fields listed in the spec. Postgres email audit log schema is untouched.
- **Caching**: DeliveryConfigurationService caches the Firestore record for 60s, invalidates on successful PUT, and exposes `ETag` plus `Last-Modified` so future request -> LLM -> email flows can short-circuit when the config changes.
- **Security**: Existing `X-SmartLetter-Api-Key` middleware guards both endpoints. Controllers derive `updatedBy` by resolving API key metadata (team, environment) from the middleware context and log only hashed key identifiers.
- **Observability**: Micrometer produces `config.update.success`, `config.update.failure`, and `config.update.unauthorized` counters with labels `(env, actorHash, sourceIp, outcome)` plus a histogram for update duration; alerts trigger when unauthorized failures >5/min or validation failures >3/min.
- **Deployment**: No new runtime targets. The existing Cloud Run service image (Paketo Buildpacks) and Terraform modules gain updated env vars/secrets so DeliveryConfigurationService knows the Firestore collection name and cache TTL. Always Free budgets (<=1 vCPU, <=256 MiB, <=20 concurrency) still apply.
- **Testing**: TDD + BDD via JUnit 5 + AssertJ, Spring Boot slice tests for controllers, Testcontainers Firestore emulator integration tests for optimistic locking, and `src/test/resources/features/configuration/config_management.feature` Cucumber scenarios tagged `@US1`, `@US2`.
- **Open questions resolved in Phase 0 research**:
  1. Firestore optimistic locking approach – originally flagged as NEEDS CLARIFICATION; resolved by Research Task R1 to use transactions with `version` preconditions and SHA-256 prompt hashing.
  2. `updatedBy` derivation – NEEDS CLARIFICATION; Research Task R2 established that API key middleware exposes `ApiKeyMetadata` so controllers can populate `updatedBy` with the key owner slug + environment, falling back to `unknown` if metadata is missing.
  3. Metric naming and alert budget – NEEDS CLARIFICATION; Research Task R3 standardized Micrometer metric names, labels, and alert thresholds so SRE dashboards remain consistent with Constitution Section II/III observability rules.

## Constitution Check

- **Interface readiness (Principle I)**: `DeliveryConfigurationController` (PUT/GET) plus DTOs are the only surface changes. Each method enforces `@Validated` constraints, sets explicit response codes (200, 201, 404, 409, 422, 503), and annotates summaries, descriptions, examples, auth headers, and error schemas so `docs/contracts/openapi.yaml` reflects the change via Springdoc. Backward compatibility is preserved because downstream callers opt into versioned `/v1/config/delivery`; no existing routes change.
- **LLM safety (Principle II)**: Feature stores prompt text but never sends it to the LLM. Sanitization rejects secrets/control characters, logs only `promptSha256`, and requires manual prompt approval before enabling downstream flows. Request -> LLM -> email coverage stays documented as a constitution exception for this story, with alerts ready for later features.
- **Email integrity (Principle III)**: No templates change, but plan reiterates that deterministic fallback templates remain the active email surface until later stories load prompts. Snapshot suites continue to enforce accessibility and markup quality.
- **Observability & fallback metrics**: New Micrometer counters/histograms plus structured logs (ConfigurationAuditEvent) capture success/failure paths. Alert thresholds (unauthorized >5/min, validation >3/min, Firestore 5xx) are defined in Risks/Mitigations and will be wired into Cloud Monitoring.
- **Container/GCP budget**: OCI images still come from Paketo Buildpacks, artifact repo `us-docker.pkg.dev/<project>/smart-letter`. Cloud Run stays in `us-central1`, 1 vCPU, 256 MiB, concurrency 20. Terraform state (`gs://smart-letter-terraform-state`) will record any env var additions. No Always Free overages expected (<100 writes/day, <10 KB storage per spec SC-006).
- **Swagger UI exposure**: `/swagger-ui` continues to load the generated contract. Try-It-Out remains enabled only in staging/test with manual API key input. Production Try-It-Out stays disabled. Test plans include exercising both endpoints via Swagger with temporary API keys before code review.
- **API key policy**: `X-SmartLetter-Api-Key` middleware enforces 32-byte entropy, constant-time comparison, rotation <=90 days, rate limits (30 writes/hr/key, 120 reads/hr/key). Controllers log hashed key IDs and map metadata to `updatedBy`. Secret Manager remains the source of keys.
- **INVEST cadence**: Stories US1–US3 map to configuration update, read/audit, and observability slices. Each has independent acceptance criteria and tests, enabling incremental PRs. WIP limit remains two simultaneous stories.
- **TDD/BDD plan**: Each story starts with failing unit + Cucumber tests: controller validation, service caching/locking, repository integrations, and `config_management.feature`. Commits will show red->green->refactor evidence (failing tests first, then implementation). No Spring Cloud Contract stubs are needed because the service has no new outbound HTTP calls.
- **IaC readiness**: Terraform module `infra/cloudrun/main.tf` gets new env vars/secret references. `infra/firestore/app_settings.tf` seeds the `delivery` document stub. Plans (`terraform plan`) attach to PRs via GitHub Actions artifacts.
- **Persistent settings**: `appSettings/configuration/delivery` schema, defaults, migration steps, cache TTL, and rollback instructions match the spec and are reiterated in `data-model.md`. Always Free quotas are respected with low write volume and emulator-based tests.
- **CI/CD automation**: `.github/workflows/ci.yml` (push) and `deploy-prod.yml` (merge) already cover lint/tests/OpenAPI/Terraform/Buildpacks/deploy. Plan requires regenerated `docs/contracts/openapi.yaml` artifacts plus new metric assertions before allowing deploy jobs to run.
- **Documentation encoding**: All new artifacts (plan, research, data-model, quickstart, contracts, tasks) remain ASCII-only. Markdown uses GitHub emoji codes when needed (none currently). Bash scripts continue to emit `[OK]/[FAIL]`. CI doc-lint (pre-commit `ascii-scan.sh`) will be cited in PR descriptions to prove compliance.

_Re-check after Phase 1 design confirms no guardrail violations; no waivers recorded in Complexity Tracking._

## Project Structure

Repository layout remains unchanged. This feature adds documentation artifacts under `specs/001-email-config-endpoint/` (plan, research, data-model, quickstart, contracts) plus IaC references and does not introduce new source modules. Application code continues to follow the existing package structure under `src/main/java/com/smartletter/` (controllers in `api`, Firestore access in `settings`).

## Phase 0 – Research

Phase 0 resolved every NEEDS CLARIFICATION item through targeted research tasks; results live in [research.md](./research.md).

| Task ID | Prompt | Outcome |
|---------|--------|---------|
| **R1** | Research Firestore optimistic locking + prompt hashing strategy for delivery configuration updates | Adopt Firestore transactions with `version` precondition and `promptSha256` hashing, documenting rollback + emulator testing.
| **R2** | Research how to derive `updatedBy` metadata from API key middleware safely | Expose `ApiKeyMetadata` via request attributes, hash key IDs, and log sanitized actor info without storing secrets.
| **R3** | Find Micrometer/alerting best practices for configuration endpoints | Standardize metric names, labels, histograms, and Cloud Monitoring alert policies for unauthorized + validation failures.

Research deliverable structure (Decision, Rationale, Alternatives) is documented in `research.md`, ensuring reviewers can trace why each approach beat the alternatives.

## Phase 1 – Design & Contracts

Artifacts created during Phase 1:

- [data-model.md](./data-model.md): Defines `DeliveryConfiguration`, `ConfigurationAuditEvent`, `DeliveryConfigurationCache`, and `ApiKeyMetadata`, including fields, validation rules, state transitions, and Firestore mappings.
- [contracts/openapi.yaml](./contracts/openapi.yaml): Provides a code-first aligned preview of the `PUT /v1/config/delivery` and `GET /v1/config/delivery` endpoints, schemas, error payloads, auth headers, and cache-related response headers. This file mirrors what Springdoc will emit once controllers are implemented; `docs/contracts/openapi.yaml` remains the canonical generated artifact.
- [quickstart.md](./quickstart.md): Offers ASCII-only setup instructions covering Maven build, Firestore emulator usage, running Cucumber/Testcontainers suites, regenerating OpenAPI, and exercising the endpoints via Swagger UI with API keys.

Agent context update:

```
./.specify/scripts/bash/update-agent-context.sh copilot
```

The script appended the new technology/context summary for Copilot, ensuring future `/speckit.tasks` steps understand the Firestore + metrics focus.

Post-design Constitution Check: All guardrails remain satisfied; no additional violations surfaced.

## Phase 2 – Implementation Plan

Execution is sliced by user story to honor INVEST and incremental delivery:

1. **US1 – Configure Delivery Target (P1)**
	- Controllers/DTOs: Scaffold `DeliveryConfigurationController.putConfig`, request/response records with validation annotations, and exception mapper for 409/422/503.
	- Service/Repository: Implement `DeliveryConfigurationService.upsert` using Firestore transaction + version precondition, `promptSha256`, caching invalidation, and `updatedBy` metadata injection.
	- Tests: Start with failing controller unit tests (validation, auth, 200 response), service tests for optimistic locking/caching, and Firestore emulator IT verifying atomic updates + rollback. Add Cucumber scenario `@US1-success`.
	- Observability: Emit success/failure logs + metrics, verify via unit tests capturing MeterRegistry counters.

2. **US2 – Audit Current Configuration (P2)**
	- Controller method: `DeliveryConfigurationController.getConfig` returning DTO with cache headers and `ETag` derived from `promptSha256` + `version`.
	- Service layer: Read-through cache with TTL <=60s, 404 when document missing, 200 with metadata otherwise.
	- Tests: Failing tests for 404, caching TTL behavior, ETag/Last-Modified headers, GET Cucumber scenario `@US2-not-found` and `@US2-success`.

3. **US3 – Surface Metrics & Alerts (P3)**
	- Metrics/logs: Add structured `ConfigurationAuditEvent`, unauthorized/validation failure counters, success duration histogram, and tie them into Cloud Monitoring alert definitions (IaC update referencing dashboards/alert policies).
	- Testing: Unit tests verifying metrics increments, log redaction, and rate-limit enforcement; integration test simulating repeated unauthorized attempts. Document alert policies in `deployment.md` (future tasks).

Cross-cutting steps per story:
- Maintain code-first OpenAPI annotations and re-run `./mvnw springdoc-openapi:generate` with every DTO/controller change, committing the regenerated `docs/contracts/openapi.yaml` diff.
- Update Terraform plan artifacts (env vars, alert policies, Firestore seeds) and attach to PR; block merge until reviewers approve plan output.
- Run full CI (lint, unit, integration, Cucumber, emulator, OpenAPI diff, Buildpacks build) before requesting review; ensure ASCII doc lint passes.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|--------------------------------------|
| _None_ | _N/A_ | _Constitution guardrails fully satisfied._ |
