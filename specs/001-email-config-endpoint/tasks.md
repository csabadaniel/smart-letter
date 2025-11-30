# Tasks: Recipient & Prompt Configuration API

**Input**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md under `specs/001-email-config-endpoint/`
**Prerequisites**: Constitution guardrails (Spring Initializr scaffold, code-first OpenAPI, LLM/email safety, ASCII-only docs) apply to every task.

**Tests**: Each user story begins with failing Cucumber + automated tests (JUnit/AssertJ + Testcontainers) before implementation per Principle IV; do not skip tests unless the spec documents an approved exception.

**Format reminder**: `- [ ] T### [P?] [US#?] Description with file path`
	- Include `[P]` only when the task is parallelizable (independent files/no deps)
	- Include `[US#]` only for user-story phases (omit during Setup/Foundational/Polish)

**Numbering guide**: Task IDs are contiguous and scoped per phase to avoid ambiguity:
- Phase 1 uses T001-T004
- Phase 2 uses T005-T010
- Phase 3 (US1) uses T011-T019
- Phase 4 (US2) uses T020-T025
- Phase 5 (US3) uses T026-T031
- Polish phase uses T032-T034

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Re-establish the Spring Initializr scaffold, dependency graph, and documentation so later phases can compile and run.
**Readiness Test**: `./mvnw --version`, `./mvnw clean verify -Pfirestore-emulator`, and `./scripts/ascii-scan.sh specs/001-email-config-endpoint` succeed on a clean clone without additional edits.

- [x] T001 Verify the Spring Initializr scaffold exists at repo root (`./pom.xml`, `./mvnw`, `./.mvn/`, `./src/`) and rerun `curl https://start.spring.io/starter.zip -d dependencies=web,validation,data-firestore,actuator -d javaVersion=21 -d language=java -d type=maven-project -d packageName=com.smartletter -o smart-letter.zip` + unzip if any generated files are missing.
- [x] T002 Update `pom.xml` to include Spring Web, Validation, Actuator, Spring Cloud GCP Firestore, Micrometer (Prometheus + logging), Springdoc OpenAPI, SHA-256 utility, and the `springdoc-openapi-maven-plugin` so PUT/GET endpoints compile and contracts regenerate.
- [x] T003 Add `delivery-config.collection-path`, `delivery-config.cache-ttl-seconds`, and API-key metadata placeholders (with comments for local/test/prod) to `src/main/resources/application.yml` so configuration values have explicit defaults.
- [x] T004 Refresh `specs/001-email-config-endpoint/quickstart.md` with the mandated Spring Initializr command, Firestore emulator env exports, API key loading guidance, and ASCII verification steps.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Provide shared configuration bindings, Firestore access, caching, and IaC plumbing required by every user story.
**Readiness Test**: `DeliveryConfigurationRepository` can read/write the Firestore document via emulator, cache TTL enforcement works, and Terraform plans show seeded config + env vars before any controller exists.

- [x] T005 Create `src/main/java/com/smartletter/settings/config/DeliveryConfigurationProperties.java` (plus register via `SmartLetterApplication`) to bind collection path, cache TTL, and rate-limit knobs defined in `application.yml`.
- [ ] T006 Implement `src/main/java/com/smartletter/security/ApiKeyMetadataResolver.java` and supporting `HandlerMethodArgumentResolver` that exposes `ApiKeyMetadata` from the API key filter to controllers/tests.
- [ ] T007 Build `src/main/java/com/smartletter/settings/firestore/DeliveryConfigurationRepository.java` using Firestore transactions with `FieldValue.increment(1)`, `promptSha256`, and emulator awareness.
- [ ] T008 Create `src/main/java/com/smartletter/settings/cache/DeliveryConfigurationCache.java` encapsulating 60-second TTL storage, `ETag` derivation, and explicit `invalidate()` hooks triggered after writes.
- [ ] T009 Add `src/test/java/com/smartletter/support/firestore/FirestoreEmulatorTestBase.java` that spins up the Testcontainers emulator, seeds JSON fixtures, and cleans collections for repository/service tests.
- [ ] T010 Update `infra/firestore/app_settings.tf` (seed `appSettings/configuration/delivery`) and `infra/cloudrun/main.tf` (pass delivery-config env vars/secrets), then capture the plan artifact referenced in PR checklists.

---

## Phase 3: User Story 1 - Configure Delivery Target (Priority: P1) :dart: MVP

**Goal**: Enable ops admins to upsert the single recipient email + deterministic LLM prompt via authenticated `PUT /v1/config/delivery`, enforcing validation, optimistic locking, caching invalidation, and observability.
**Independent Test**: Using Swagger UI (with API key) to call PUT should persist Firestore data + metadata, increment metrics/logs, invalidate cache, and be verifiable via direct Firestore query without restarting the service.

### Tests (fail first)

- [ ] T011 [P] [US1] Expand `src/test/resources/features/configuration/config_management.feature` with `@US1-success`, `@US1-validation-error`, and `@US1-conflict` scenarios plus glue under `src/test/java/com/smartletter/bdd/configuration/`.
- [ ] T012 [P] [US1] Add `DeliveryConfigurationControllerPutTest` in `src/test/java/com/smartletter/settings/api/` covering validation, auth, 200/201/409/422 mappings, and audit header assertions via MockMvc.
- [ ] T013 [P] [US1] Create `DeliveryConfigurationServiceUpsertIT` in `src/test/java/com/smartletter/settings/service/` exercising Firestore transaction rollback, version incrementing, cache invalidation, and prompt hashing via the emulator base.

### Implementation

- [ ] T014 [US1] Implement `DeliveryConfigurationRequest`/`DeliveryConfigurationResponse` records with `jakarta.validation` + Springdoc annotations in `src/main/java/com/smartletter/settings/api/` including example values + descriptions.
- [ ] T015 [US1] Implement `DeliveryConfigurationService.upsert` in `src/main/java/com/smartletter/settings/service/DeliveryConfigurationService.java` to run Firestore transaction, compute `promptSha256`, set `updatedBy/updatedAt/version`, and invalidate the cache.
- [ ] T016 [US1] Implement `DeliveryConfigurationController.putConfig` in `src/main/java/com/smartletter/settings/api/DeliveryConfigurationController.java` using the argument resolver, mapping domain exceptions to `ApiErrorHandler`, and returning response headers.
- [ ] T017 [P] [US1] Add `src/main/java/com/smartletter/settings/observability/ConfigUpdateMetrics.java` (Micrometer counters/histogram) plus `ConfigurationAuditEvent` logging to emit sanitized success/failure entries.
- [ ] T018 [US1] Regenerate `docs/contracts/openapi.yaml` via `./mvnw springdoc-openapi:generate`, review diff vs. `specs/001-email-config-endpoint/contracts/openapi.yaml`, and commit the updated artifact.
- [ ] T019 [US1] Document PUT-specific runbook steps (optimistic locking guidance, sample payloads, error codes) in `docs/runbooks/config-governance.md`.

**Checkpoint**: PUT endpoint passes automated suites and manual Swagger validation; metrics/log entries visible locally.

---

## Phase 4: User Story 2 - Audit Current Configuration (Priority: P2)

**Goal**: Provide authenticated `GET /v1/config/delivery` returning the latest configuration, audit metadata, and cache headers while handling missing configs gracefully.
**Independent Test**: Calling GET after a PUT returns 200 with DTO + `ETag`/`Last-Modified` headers; calling GET before any PUT returns 404 `CONFIG_NOT_FOUND`, all verified via BDD + integration tests without invoking downstream flows.

### Tests (fail first)

- [ ] T020 [P] [US2] Extend `config_management.feature` with `@US2-success` and `@US2-not-found` scenarios plus glue for cache-header assertions in `src/test/java/com/smartletter/bdd/configuration/`.
- [ ] T021 [P] [US2] Add `DeliveryConfigurationServiceCacheTest` in `src/test/java/com/smartletter/settings/service/` verifying 60s TTL, `ETag` derivation (`version` + `promptSha256`), and 404 exception semantics.

### Implementation

- [ ] T022 [US2] Implement `DeliveryConfigurationService.getConfiguration` in `src/main/java/com/smartletter/settings/service/DeliveryConfigurationService.java` to serve cache hits, refresh on miss, and throw typed exception when Firestore lacks the document.
- [ ] T023 [US2] Implement `DeliveryConfigurationController.getConfig` in `src/main/java/com/smartletter/settings/api/DeliveryConfigurationController.java` adding cache-control headers, `ETag`, and API key enforcement.
- [ ] T024 [US2] Update `src/main/java/com/smartletter/settings/api/ApiErrorHandler.java` (or equivalent) to emit `CONFIG_NOT_FOUND` payloads with RFC7807 details and to set cache headers on successful responses.
- [ ] T025 [US2] Capture GET usage, cache semantics, staging vs. production Swagger Try-It-Out rules, and deployment gating steps in `docs/runbooks/config-governance.md`.

**Checkpoint**: GET endpoint independently testable via automated suites + Swagger UI, including 404 behavior.

---

## Phase 5: User Story 3 - Surface Metrics & Alerts (Priority: P3)

**Goal**: Emit Micrometer counters/histograms and structured logs for every config access/update and wire Cloud Monitoring alert policies for misuse patterns.
**Independent Test**: Automated tests drive success/failure/unauthorized paths, assert metrics/log payloads, and Terraform plan shows the new alert policies; chaos test proves unauthorized bursts trigger notifications within 1 minute.

### Tests (fail first)

- [ ] T026 [P] [US3] Add `@US3-observability` scenarios to `config_management.feature` plus glue verifying metric increments/log redaction in `src/test/java/com/smartletter/bdd/configuration/`.
- [ ] T027 [P] [US3] Implement `ConfigUpdateMetricsTest` in `src/test/java/com/smartletter/settings/observability/` asserting counter, histogram, and unauthorized rate-limit gauges populate correct labels.

### Implementation

- [ ] T028 [US3] Finalize `src/main/java/com/smartletter/settings/observability/ConfigurationAuditEvent.java` (or similar) to publish structured logs with hashed actors, prompt hashes, and correlation IDs.
- [ ] T029 [US3] Wire Micrometer counters/histograms plus unauthorized rate-limit gauges into controller/service layers and expose via `/actuator/prometheus` by updating `src/main/java/com/smartletter/settings/observability/ConfigUpdateMetrics.java`.
- [ ] T030 [US3] Update `infra/monitoring/config.tf` to add Cloud Monitoring alert policies for `config.update.failure` (>3/min) and `config.update.unauthorized` (>5/min) with Slack/webhook notifications and Terraform outputs documenting targets.
- [ ] T031 [US3] Extend `docs/runbooks/alerts.md` with alert meanings, suppression workflow, and chaos-test validation steps referencing metrics/log IDs.

**Checkpoint**: Metrics/logs observable locally and alert Terraform plan reviewed/attached to PR evidence.

---

## Final Phase: Polish & Cross-Cutting

**Purpose**: Validate documentation, CI/CD automation, and ASCII compliance before requesting review/deploy.
**Independent Test**: Quickstart steps succeed on a fresh machine, ASCII/doc lint passes, Swagger UI validated in staging, and CI artifacts (OpenAPI, Terraform plan, emulator logs) are attached to the PR.

- [ ] T032 [P] Run the quickstart flow end-to-end (tests, OpenAPI generation, Swagger PUT/GET via API key) and capture screenshots/notes in `specs/001-email-config-endpoint/quickstart.md`, including production Try-It-Out restrictions.
- [ ] T033 [P] Ensure `.github/workflows/ci.yml` and `.github/workflows/deploy-prod.yml` include new suites (`-Pfirestore-emulator`, observability tests), artifact uploads (OpenAPI, Terraform plan, Cucumber report), and required approvals for production deploys.
- [ ] T034 Run `./scripts/ascii-scan.sh specs/001-email-config-endpoint` and update `docs/RELEASE_NOTES.md` with links to CI runs, Terraform plan artifacts, and alert policy diffs.

---

## Dependencies & Execution Order

- Phase 1 (Setup) must complete before Phase 2 because later work depends on the regenerated scaffold, dependencies, and documented quickstart.
- Phase 2 (Foundational) blocks all user stories; repository, cache, argument resolver, and IaC wiring are prerequisites for every endpoint.
- User Story 1 (US1, P1) delivers the MVP and must finish before US2 and US3 rely on persisted values, but once DTO/service contracts stabilize, US2 testing can begin in parallel.
- User Story 2 (US2, P2) depends on the cache/service logic introduced in US1 but remains independently testable; completion unlocks observability work that needs GET hooks.
- User Story 3 (US3, P3) depends on the metrics/log points added in US1/US2 plus Terraform scaffolding; alert IaC can run after those hooks exist.
- Polish phase runs after targeted user stories are complete to capture manual validation, CI updates, and ASCII checks.

## Parallel Execution Examples

- **US1**: One engineer can own BDD + controller tests (T011-T012) while another builds repository/service integration (T013-T015); observability instrumentation (T017) proceeds in parallel after service signatures stabilize.
- **US2**: While T020 expands BDD scenarios, another engineer can implement service/controller changes (T022-T023) and error handling (T024); documentation (T025) finalizes once responses are stable.
- **US3**: Terraform alert work (T030) proceeds independently of code-level logging/metric tasks (T028-T029), while BDD + observability tests (T026-T027) validate behavior concurrently.

## Implementation Strategy

1. **MVP First**: Complete Phases 1-2, then finish US1 (tests + PUT endpoint) to unlock basic configuration management and demo via Swagger UI and CI artifacts.
2. **Incremental Delivery**: Layer US2 to provide read visibility and deployment gating evidence before addressing observability; ensure each story is independently testable before moving on.
3. **Parallel Teams**: After foundational work, assign engineers per user story as shown above to maintain low WIP while still shipping INVEST-compliant slices; keep Terraform/doc tasks in lockstep with code changes so audits pass.
```
