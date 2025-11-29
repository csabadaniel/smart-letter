# Tasks: Recipient & Prompt Configuration API

**Input**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md under `specs/001-email-config-endpoint/`
**Prerequisites**: Constitution guardrails (code-first OpenAPI, LLM safety, ASCII docs) remain in force for every task

**Tests**: Each user story begins with failing Cucumber + automated tests (JUnit/AssertJ + Testcontainers) before implementation per Principle IV.

**Format reminder**: `- [ ] T### [P] [US#] Description with file path`

## Phase 1: Setup (Shared Infrastructure)

**Goal**: Ensure the repo has the dependencies, config surface, and documentation hooks needed before feature work begins.
**Readiness Test**: `./mvnw --version`, `./mvnw clean verify -Pfirestore-emulator` and `./scripts/ascii-scan.sh specs/001-email-config-endpoint` all succeed without code changes.

- [ ] T001 Update `pom.xml` with Spring Cloud GCP Firestore starter, Micrometer meter-registry bindings, and SHA-256 utility dependency so config endpoints can compile.
- [ ] T002 Add `delivery-config.collection-path`, `delivery-config.cache-ttl-seconds`, and API-key metadata placeholders to `src/main/resources/application.yml` plus document defaults/comments for every environment.
- [ ] T003 Refresh `specs/001-email-config-endpoint/quickstart.md` to include Firestore emulator env exports, API key loading instructions, ASCII-only verification steps, and the exact Spring Initializr configuration mandated by Constitution v2.2.0.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Goal**: Establish configuration properties, Firestore access, caching, and IaC hooks required by all stories.
**Readiness Test**: A skeleton service can read/write the Firestore document via emulator using `DeliveryConfigurationRepository` and exposes bounded cache TTL before any endpoint logic exists.

- [ ] T004 Create `src/main/java/com/smartletter/settings/config/DeliveryConfigurationProperties.java` with `@ConfigurationProperties` binding for collection path, cache TTL, and rate-limit knobs; register it via `@EnableConfigurationProperties`.
- [ ] T005 Implement `src/main/java/com/smartletter/security/ApiKeyMetadataResolver.java` that pulls `ApiKeyMetadata` from the authentication filter and exposes it through a `HandlerMethodArgumentResolver` for controllers/tests.
- [ ] T006 Build `src/main/java/com/smartletter/settings/firestore/DeliveryConfigurationRepository.java` using the Firestore SDK with transactional `upsert` and `find` methods plus emulator configuration awareness.
- [ ] T007 Add `src/main/java/com/smartletter/settings/cache/DeliveryConfigurationCache.java` (and supporting config) implementing 60-second TTL caching with explicit `invalidate()` hooks.
- [ ] T008 Introduce `src/test/java/com/smartletter/support/firestore/FirestoreEmulatorTestBase.java` that boots Testcontainers emulator, seeds JSON fixtures, and tears down collections for reuse across repository/service tests.
- [ ] T009 Update IaC: modify `infra/firestore/app_settings.tf` to seed the `appSettings/configuration/delivery` stub and `infra/cloudrun/main.tf` to pass the new env vars, then capture/attach the resulting `terraform plan` artifact.

---

## Phase 3: User Story 1 - Configure Delivery Target (Priority: P1) :dart: MVP

**Goal**: Allow ops admins to securely upsert the single recipient email and deterministic LLM prompt via `PUT /v1/config/delivery`.
**Independent Test**: Using Swagger UI (with API key) to call the PUT endpoint should persist values in Firestore + cache, emit audit logs/metrics, and reflect the change immediately through a direct Firestore query without restarting the service.

### Tests (fail first)

- [ ] T010 [P] [US1] Expand `src/test/resources/features/configuration/config_management.feature` with `@US1-success`, `@US1-validation-error`, and `@US1-conflict` scenarios describing PUT flows.
- [ ] T011 [P] [US1] Add `DeliveryConfigurationControllerTest` in `src/test/java/com/smartletter/settings/api/` covering validation, API key auth requirement, and HTTP 200/422/409 mappings.
- [ ] T012 [P] [US1] Create `DeliveryConfigurationRepositoryIT` in `src/test/java/com/smartletter/settings/firestore/` exercising optimistic locking, transaction rollback, and prompt hashing against the emulator.

### Implementation

- [ ] T013 [US1] Implement DTOs `DeliveryConfigurationRequest`/`DeliveryConfigurationResponse` with `jakarta.validation` + Springdoc annotations in `src/main/java/com/smartletter/settings/api/`.
- [ ] T014 [US1] Implement `DeliveryConfigurationService.upsert` in `src/main/java/com/smartletter/settings/service/` to run Firestore transaction, compute `promptSha256`, populate `updatedBy`, invalidate cache, and return the response model.
- [ ] T015 [US1] Implement `DeliveryConfigurationController.putConfig` in `src/main/java/com/smartletter/settings/api/` using the argument resolver, mapping exceptions to `ApiErrorHandler`, and wiring metrics/log calls.
- [ ] T016 [P] [US1] Instrument Micrometer counters/histogram plus `ConfigurationAuditEvent` structured logging in `src/main/java/com/smartletter/settings/observability/ConfigUpdateMetrics.java`.
- [ ] T017 [US1] Regenerate and commit `docs/contracts/openapi.yaml` via `./mvnw springdoc-openapi:generate`, ensuring PUT schema + examples match controller annotations.

**Checkpoint**: Swagger UI PUT succeeds end-to-end using emulator; all US1 tests green.

---

## Phase 4: User Story 2 - Audit Current Configuration (Priority: P2)

**Goal**: Provide `GET /v1/config/delivery` so the compliance team can read the latest configuration + audit metadata with cache/etag headers.
**Independent Test**: Calling GET with API key returns 200 and headers after a prior PUT; calling GET before any PUT returns 404 `CONFIG_NOT_FOUND`, and both cases are covered by BDD + integration tests without invoking downstream pipelines.

### Tests (fail first)

- [ ] T018 [P] [US2] Extend `src/test/resources/features/configuration/config_management.feature` with `@US2-success` and `@US2-not-found` scenarios covering GET flows.
- [ ] T019 [P] [US2] Add `DeliveryConfigurationServiceTest` in `src/test/java/com/smartletter/settings/service/` verifying cache TTL, ETag derivation, and 404 handling via emulator fakes.

### Implementation

- [ ] T020 [US2] Implement `DeliveryConfigurationService.getConfiguration()` returning cached data, populating `ETag`/`Last-Modified`, and throwing typed exceptions when missing.
- [ ] T021 [US2] Add `DeliveryConfigurationController.getConfig` with `@GetMapping`, cache-control headers, and API key auth in `src/main/java/com/smartletter/settings/api/`.
- [ ] T022 [US2] Update `src/main/java/com/smartletter/settings/api/ApiErrorHandler.java` (or equivalent) to emit typed `CONFIG_NOT_FOUND` payloads and add header writers for ETag/Cache-Control.
- [ ] T023 [US2] Document GET usage, cache semantics, staging-vs-production Swagger Try-It-Out policy, and deployment gating steps in `docs/runbooks/config-governance.md`.

**Checkpoint**: GET endpoint independently testable via Swagger and automated suites.

---

## Phase 5: User Story 3 - Surface Metrics & Alerts (Priority: P3)

**Goal**: Emit Micrometer metrics and structured logs for every config access/update and provision Cloud Monitoring alerts for misuse patterns.
**Independent Test**: Automated tests generate success/failure/unauthorized events and assert counters/log payloads; Terraform plan shows new alert policies, and a chaos test proves unauthorized attempts trigger notifications within 1 minute.

### Tests (fail first)

- [ ] T024 [P] [US3] Add `@US3-observability` scenarios to `src/test/resources/features/configuration/config_management.feature` covering unauthorized bursts and expected alerts.
- [ ] T025 [P] [US3] Implement `ConfigUpdateMetricsTest` in `src/test/java/com/smartletter/settings/observability/` asserting counter/histogram labels and log redaction.

### Implementation

- [ ] T026 [US3] Implement `ConfigurationAuditEvent` + log writer in `src/main/java/com/smartletter/settings/observability/` ensuring prompt hashes (not plaintext) and correlation IDs.
- [ ] T027 [US3] Wire Micrometer counters, histogram, and rate-limit gauges into controller/service layers plus expose them via `/actuator/prometheus`.
- [ ] T028 [US3] Update `infra/monitoring/config.tf` to create Cloud Monitoring alert policies for `config.update.failure` (>3/min) and `config.update.unauthorized` (>5/min) with Slack/webhook notifications.
- [ ] T029 [US3] Add runbook entries in `docs/runbooks/alerts.md` describing alert meaning, suppression procedure, and validation steps for chaos testing.

**Checkpoint**: Metrics/logs visible locally and alert Terraform plan reviewed.

---

## Final Phase: Polish & Cross-Cutting

**Goal**: Harden documentation, deployment evidence, and manual validations before merge/deploy.
**Independent Test**: Quickstart steps run cleanly on a fresh machine, ASCII/doc lint passes, Swagger UI validated in staging, and IaC/CI artifacts attached to PR.

- [ ] T030 [P] Run quickstart flow end-to-end (tests, OpenAPI generation, staging Swagger UI manual PUT/GET with API key) and capture evidence in `specs/001-email-config-endpoint/quickstart.md` including the production Try-It-Out restriction.
- [ ] T031 [P] Ensure `.github/workflows/ci.yml` and `deploy-prod.yml` include new test suites (`-Pfirestore-emulator`, observability tests) plus artifact uploads for OpenAPI + Terraform plan.
- [ ] T032 Confirm ASCII-only documentation by running `./scripts/ascii-scan.sh specs/001-email-config-endpoint` and update `docs/RELEASE_NOTES.md` with pointers to CI runs, Terraform plan, and alert policy diffs.

---

## Dependencies & Execution Order

- **Phase 1** must finish before Foundation because dependencies/config placeholders are required by all later work.
- **Phase 2** blocks every user story; repository, cache, argument resolver, and IaC wiring are prerequisites.
- **US1 (P1)** delivers the MVP and must finish before US2/US3 rely on persisted values, but once US1 code skeleton exists, US2 can start on the GET path while US1 tests still run.
- **US3** depends on metrics/log hooks introduced in US1/US2 but can start in parallel once those code branches exist (e.g., after controller/service scaffolding lands).
- **Polish** runs after all desired stories are completed.

## Parallel Execution Examples

- **US1**: One engineer owns BDD + controller tests (T010-T011) while another implements repository/service logic (T012-T014). Metrics/log instrumentation (T016) can run in parallel once service signatures stabilize.
- **US2**: While T018 (BDD) runs, another engineer completes service cache tests (T019) and implementation tasks T020-T022 concurrently because they touch different classes (service vs. controller) yet rely on the same DTOs.
- **US3**: Terraform alert work (T028) proceeds independently of code-level logging tasks (T026-T027); both unblock only when Micrometer metric names are finalized from US1 research.

## Implementation Strategy

1. **MVP First**: Finish Phases 1-2, deliver US1 entirely (tests + PUT endpoint), and demo via Swagger UI/CI artifacts.
2. **Incremental Delivery**: Ship US2 next (read visibility) so downstream teams can verify deployment gates; then US3 adds observability/alerts without touching path-critical code.
3. **Parallel Teams**: After foundational work, allocate devs per user story as outlined in the parallel examples to minimize idle time while keeping INVEST slices independently testable.
