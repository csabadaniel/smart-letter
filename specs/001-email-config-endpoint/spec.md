# Feature Specification: Recipient & Prompt Configuration API

**Feature Branch**: `001-email-config-endpoint`  
**Created**: 2025-11-23  
**Status**: Draft  
**Input**: User description: "Expose an API endpoint that stores the single recipient email address and the LLM prompt text used to craft outbound emails; both values are mandatory and have no defaults"

## User Scenarios & Testing *(mandatory)*
### User Story 1 - Configure Delivery Target (Priority: P1)

As the Smart Letter ops admin, I want to update the single recipient email address and the deterministic LLM prompt through a secure API so that the downstream service always uses approved, audited configuration values.

**Why this priority**: Without a governed configuration endpoint the service cannot send compliant emails; this is the first requirement to unlock any letter delivery.

**Independent Test**: Invoke `PUT /v1/config/delivery` with valid data via Swagger UI (Try-It-Out authenticated with API key); verify Firestore stores the new record + audit log, and the GET endpoint reflects the change without restarting the service.
**Acceptance Scenarios**:

1. **Given** no delivery configuration exists, **When** ops submits a valid email + prompt, **Then** the system persists both values atomically and returns HTTP 200 with the stored payload + version stamp.
2. **Given** an existing configuration, **When** ops submits a new prompt with the same email, **Then** the API overwrites only the prompt, bumps the version, records who changed it, and surfaces the new revision in Firestore + metrics.
3. **Given** ops submits an invalid email syntax or an empty prompt, **When** validation runs, **Then** the API responds with HTTP 422, lists the offending fields, and leaves Firestore unchanged.
4. **Given** concurrent administrators issue updates, **When** optimistic locking detects a stale `version`, **Then** the API responds with HTTP 409 instructing the caller to re-fetch before retrying.

---
### User Story 2 - Audit Current Configuration (Priority: P2)

As the compliance reviewer, I need to query the currently effective recipient email and prompt together with audit metadata so I can prove the system is configured before triggering any request -> LLM -> email pipeline in later features.

**Why this priority**: Read visibility is required for change review, runbooks, and rollout planning; without it we cannot demonstrate readiness to invoke the downstream pipeline.

**Independent Test**: Call `GET /v1/config/delivery` with the platform API key and assert the response contains the latest values, immutable audit metadata, and cache headers; contract + BDD tests verify the shape without touching the email pipeline.
**Acceptance Scenarios**:

1. **Given** a stored configuration, **When** the GET endpoint is invoked, **Then** the API returns HTTP 200 with email, prompt, version, last-modified timestamp, and modifier identity.
2. **Given** no configuration exists, **When** the GET endpoint is invoked, **Then** the API responds with HTTP 404 and a descriptive error instructing ops to create the configuration first.
3. **Given** a caller lacks the API key, **When** they hit either endpoint, **Then** authentication fails with HTTP 401 and the attempt is logged.

---
### User Story 3 - Surface Metrics & Alerts (Priority: P3)

As the SRE, I want configuration update attempts (success, validation failure, unauthorized) published as Micrometer metrics and structured logs so I can monitor misuse before email delivery features launch.

**Why this priority**: Early observability ensures later stories can trust configuration provenance and catch tampering quickly.

**Independent Test**: Run integration tests that exercise success + failure paths and assert metrics/log outputs via Testcontainers + log capture; verify GitHub Actions exports artifacts showing the counters/histograms.
**Acceptance Scenarios**:

1. **Given** a successful update, **When** the request completes, **Then** `config.update.success` increments with labels (env, actor, source IP) and the structured log contains the hash of the stored prompt (never the plaintext) plus Firestore document version.
2. **Given** validation fails, **When** the API responds 422, **Then** `config.update.failure` increments with `reason=validation` and the error payload omits the raw prompt.
3. **Given** repeated unauthorized attempts, **When** more than 5 failures occur in 1 minute, **Then** the alert defined in the plan fires and notifies the platform channel.

### Edge Cases

- Duplicate submissions with identical payloads -> leverage Firestore versioning so the second call becomes a no-op yet still returns HTTP 200 with the current version (FR-017, Persistent Settings Store).
- Conflicting updates arriving milliseconds apart -> optimistic locking via `version` or `updateTime` ensures one succeeds while the other receives HTTP 409 with re-fetch guidance (FR-017).
- Missing configuration while downstream services attempt to access it -> GET endpoint returns 404; downstream callers must treat this as a deployment gate and block the request -> LLM -> email pipeline until configuration exists (User Story 2 note).
- Prompt text exceeding safe length or containing secrets -> validation enforces max length (e.g., 4k chars) and redaction rules before persisting (LLM & Email Safeguards, FR-017).
- Firestore quota exhaustion during writes -> API responds 503 with retry-after header, increments failure metric, and emits alert so ops can pause updates (FR-014, Containerization & GCP Deployment).
- Swagger UI outdated after spec regeneration -> build fails via `springdoc-openapi:generate` diff if committed YAML drifts from annotated controllers (FR-001, FR-010).
- Unauthorized automation hitting the endpoint -> API key middleware rejects the call (FR-009) and metrics/alerts capture the repeated failures (User Story 3).

### Functional Requirements

- **FR-017**: Provide an authenticated `PUT /v1/config/delivery` endpoint that accepts exactly two required fields (`recipientEmail`, `llmPrompt`), enforces validation (RFC 5322 email, prompt length 1-4000 chars, printable ASCII), strips leading/trailing whitespace, and upserts a single Firestore `AppSetting` document with optimistic locking + version increments.
- **FR-018**: Provide `GET /v1/config/delivery` that returns the current configuration payload, audit metadata (version, updatedBy, updatedAt), and cache headers (`ETag`, `Last-Modified`) while honoring API key auth; when no document exists, return HTTP 404 with typed error code `CONFIG_NOT_FOUND`.
- **FR-019**: Persist configuration changes in Firestore with encryption-at-rest, redact the raw prompt from structured logs (store only SHA-256 hash), and emit Micrometer metrics/log entries for success/failure including actor identity (derived from API key metadata).
- **FR-020**: Surface configuration values to downstream beans via a typed `DeliveryConfigurationService` that caches the Firestore document with TTL <= 60s and invalidates on successful updates; future request -> LLM -> email flows must consume this service instead of environment variables.

### Runtime Stack & Scaffolding *(constitution-required)*

- Service code continues to target Java 21 + Spring Boot 3.3.x with controllers under `com.smartletter.api` and settings/services under `com.smartletter.settings`, matching the original Spring Boot CLI scaffold (`spring init --dependencies=web,validation,data-firestore,actuator`); any new components for this feature must keep using official Spring Boot Starters instead of ad-hoc wiring.
- Build inputs remain Maven Wrapper (`./mvnw ...`) + Paketo Buildpacks so container images inherit the same baseline as every other Constitution-governed service; deviations (e.g., manual Dockerfiles) are disallowed unless documented as a waiver in Complexity Tracking.
- Code reviews require evidence (CLI command snippet or generated files) that new modules/classes align with the CLI-generated structure, ensuring cross-cutting auto-configuration (validation, actuator, Micrometer) stays consistent without custom bootstrapping.

### LLM & Email Safeguards *(constitution-required)*

- This feature does not contact the LLM directly; it stores the prompt that future stories will pass to the request -> LLM -> email pipeline. Constitution Check will record the temporary exception for live pipeline coverage.
- Prompt text must be reviewed/approved by product + compliance before deployment; store the approved Markdown/HTML snippet inside Firestore plus a sanitized hash to log.
- Sanitization rules: reject inputs containing literal API keys/secrets (regex-based), control characters, or script tags. CI includes unit tests for the sanitization helper plus Cucumber scenarios showing the rejection paths.
- Deterministic fallback templates remain unchanged in this feature; document pointer to `src/main/resources/templates/fallback/` and confirm snapshot tests already cover them.
- Alerts: `config.update.failure` > 3/min triggers `LLM Prompt Config Failure` page because inability to store prompts blocks later email flows.

### Containerization & GCP Deployment *(constitution-required)*

- Continue using Paketo Buildpacks via `./mvnw spring-boot:build-image`, tag images as `gcr.io/<project>/smart-letter:<commit-sha>`, and promote via Terraform apply referencing that digest.
- Artifact Registry repo `us-docker.pkg.dev/<project>/smart-letter` stores the image; SBOM produced by Buildpacks is uploaded as workflow artifact; vulnerability scan gate must pass before deploy job runs.
- Cloud Run configuration remains `us-central1`, 1 vCPU, 256 MiB, max concurrency 20, min instances 0, CPU on-demand—well inside Always Free assumptions.
- Deployments continue through Terraform (`infra/cloudrun/main.tf`) plus helper script `infra/scripts/deploy-cloudrun.sh` documenting `gcloud run services replace`; include the new config service env vars/secrets (Firestore collection name) within IaC.
- Terraform state stored in GCS backend `gs://smart-letter-terraform-state`; attach plan output to PR (FR-015) referencing new variables.
- Rollback: redeploy previous image digest + revert Firestore document if the new schema fails; measure cold start via `config.update.success` duration metric to ensure < 500 ms p95.

### Persistent Settings Store *(constitution-required)*

- Collection `appSettings/configuration` stores a single document `delivery` with fields: `recipientEmail (string)`, `llmPrompt (string)`, `version (int)`, `updatedBy (string)`, `updatedAt (timestamp)`, `promptSha256 (string)`. No defaults; CI seeds explicit fixture via emulator script.
- Migration strategy: Terraform fixture creates empty document stub (status `pending`) during initial rollout; post-deploy runbook requires ops to populate actual values via API before enabling downstream features. Rollback = reinstate previous document snapshot captured in Firestore exports.
- Cache: `DeliveryConfigurationService` caches doc for 60 seconds with ETag; stale detection occurs when Firestore `updateTime` changes or config endpoint returns new `version` causing invalidation.
- Integration tests start emulator (`mvn test -Pfirestore-emulator`), seed fixture via JSON import, and delete the collection afterward; CI ensures Always Free quotas unaffected because emulator handles data locally.

### Continuous Delivery Automation *(constitution-required)*

- Reference the GitHub Actions workflows that run on push (test deploy) and merge (production deploy), including job names, secrets, and artifact outputs.
- Detail the quality gates (tests, coverage minimums, Terraform plan diffs, SBOM, container scan) and how failing gates block the deploy steps.
- Describe how workflow runs annotate Cloud Run revisions with commit SHAs, publish environment URLs, and notify stakeholders (Slack/email) when deployments complete or fail.
- Outline rollback/roll-forward expectations and manual intervention steps when test or production deployments require remediation.
- `ci.yml` (push) runs lint, unit, integration, contract, BDD, coverage, Springdoc generation diff, Terraform plan, Buildpacks build + scan; upon success it deploys to the **test** Cloud Run service and posts summary with `[OK]`/`[FAIL]` statuses in the job logs.
- `deploy-prod.yml` (merge to main) reruns the entire gate plus `terraform apply` and production deploy; failure halts release and requires manual approval to retry.
- Both workflows publish artifacts: JUnit XML, Cucumber reports, `docs/contracts/openapi.yaml`, Terraform plan, SBOM, container digest; they also annotate Cloud Run revisions with the commit SHA and emit Slack webhook notifications.
- Rollback expectations: revert commit + rerun workflow or trigger manual job with previous digest; document manual steps in the runbook referenced by tasks.md.

### Documentation Encoding Discipline *(constitution-required)*

- All feature artifacts (spec, plan, research, data model, quickstart, tasks, checklists) remain ASCII-only; contributors run `./scripts/ascii-scan.sh specs/001-email-config-endpoint` locally before commits, and CI blocks merges if the scanner detects Unicode.
- Markdown files may use GitHub emoji codes (e.g., `:warning:`) but this specification currently relies solely on ASCII punctuation; reviewers confirm diffs show ASCII via the doc lint output linked in PRs.
- Bash/CLI snippets embedded in documentation keep `[OK]` / `[FAIL]` style status tags so automated scripts and humans can read them without Markdown rendering.

### Access Control & API Keys *(constitution-required)*

- Continue using `X-SmartLetter-Api-Key` with 32-byte randomness; configuration endpoints sit behind the same middleware and future IAM-protected Cloud Run ingress.
- Keys live in Cloud Secret Manager (`smart-letter-api-key-{env}`) and are injected via Cloud Run env var; middleware logs success/failure with hashed key ID.
- Rate limit: 30 config writes/hour/key, 120 reads/hour/key; Micrometer counters feed alert `config.update.rate_limit_exceeded`.
- Swagger UI prompts for the API key, stores it in session storage only, and ensures Try-It-Out uses the identical header; docs remind admins to clear browser storage after use.
- Swagger UI Try-It-Out remains enabled in staging/test only; production Swagger stays behind identity-aware proxy with Try-It-Out disabled, so validation occurs via dedicated QA requests that still honor the API key header.

### Testing Discipline (TDD + BDD) *(constitution-required)*

- Begin with failing tests: `DeliveryConfigurationControllerTest` (validation), `DeliveryConfigurationServiceTest` (caching + optimistic locking), `DeliveryConfigurationRepositoryIT` (Firestore emulator). Coverage target 90%+.
- BDD scenarios (`src/test/resources/features/configuration/config_management.feature`) cover successful update, validation failure, optimistic locking conflict, and GET 404; tag as `@US1`, `@US2`.
- Spring Cloud Contract not needed (no external HTTP dependencies), but Firestore emulator (Testcontainers) ensures repository logic works; no LLM call tests for this feature.
- CI executes `./mvnw verify -Pcucumber -Pfirestore-emulator` in parallel with unit suite; artifacts include Cucumber HTML report + emulator logs.

### Key Entities *(include if feature involves data)*

- **DeliveryConfiguration**: Aggregates the single `recipientEmail`, `llmPrompt`, and metadata fields (version, updatedBy, updatedAt, promptSha256) persisted in Firestore and surfaced via API/cache.
- **ConfigurationAuditEvent**: Structured log entry generated on every update attempt capturing actor, outcome, sanitized context, and correlation ID for compliance export.
- **DeliveryConfigurationService**: Spring bean responsible for reading/writing Firestore data, enforcing optimistic locking, and exposing cached config to future request -> LLM -> email flows.
- **ApiKeyMetadata**: Internal representation binding each API key to owner, environment, and rate-limit bucket used when logging or alerting on configuration access.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of configuration writes succeed (HTTP 200) within 1 second p95 under nominal load once validation passes.
- **SC-002**: 100% of invalid submissions respond with HTTP 4xx explaining the exact field within 200 ms p95.
- **SC-003**: GET requests return the latest configuration within 100 ms p95 and include `ETag` headers consumed by clients in at least one integration test.
- **SC-004**: Every configuration change emits a structured audit log entry and increments metrics that appear on the Config Governance dashboard within 1 minute.
- **SC-005**: GitHub Actions workflows for this branch publish regenerated `docs/contracts/openapi.yaml` artifacts on every run and fail if the generated file differs from the committed version.
- **SC-006**: Firestore document updates for this feature remain below 100 writes/day and < 10 kB storage, keeping Always Free usage under 1% of quotas.
- **SC-007**: Swagger UI in staging demonstrates both endpoints with successful Try-It-Out execution using a temporary API key before code review completes.
- **SC-008**: Coverage on `DeliveryConfiguration*` classes stays >= 95%, and at least four Cucumber scenarios run green in CI for this feature.
- **SC-009**: Alerting pipeline detects and notifies on ≥5 unauthorized configuration attempts within 60 seconds during chaos testing.
