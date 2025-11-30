---

description: "Task list template for feature implementation"
---

# Tasks: [FEATURE NAME]

**Input**: Design documents from `/specs/[###-feature-name]/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Per Constitution Principle IV, every user story MUST begin with living BDD scenarios plus failing automated tests (unit, integration, contract). Only skip tests if the specification explicitly documents an exception and reviewers accept the risk.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions
- Map each story to the BDD scenario IDs defined in `spec.md` so traceability is auditable

**Encoding Rule**: Keep this document ASCII-only. Represent any visual cues with GitHub Markdown emoji codes (e.g., `:warning:`, `:white_check_mark:`) inside Markdown, but ensure Bash scripts/terminal snippets referenced here use ASCII tags like `[OK]` / `[FAIL]` instead of Markdown emoji codes.

## Path Conventions

- **Single project**: `src/`, `tests/` at repository root
- **Web app**: `backend/src/`, `frontend/src/`
- **Mobile**: `api/src/`, `ios/src/` or `android/src/`
- Paths shown below assume single project - adjust based on plan.md structure

<!-- 
  ============================================================================
  IMPORTANT: The tasks below are SAMPLE TASKS for illustration purposes only.
  
  The /speckit.tasks command MUST replace these with actual tasks based on:
  - User stories from spec.md (with their priorities P1, P2, P3...)
  - Feature requirements from plan.md
  - Entities from data-model.md
  - Endpoints from contracts/
  
  Tasks MUST be organized by user story so each story can be:
  - Implemented independently
  - Tested independently
  - Delivered as an MVP increment
  
  DO NOT keep these sample tasks in the generated tasks.md file.
  ============================================================================
-->

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [ ] T001 Scaffold Spring Boot 3.3 project via Spring Initializr (https://start.spring.io or `curl https://start.spring.io/starter.zip`) and Maven Wrapper (`./mvnw`) with base package `com.smartletter`
- [ ] T002 Configure Springdoc/OpenAPI code-first generation (annotate baseline controllers) so `./mvnw springdoc-openapi:generate` produces `docs/contracts/openapi.yaml`; document the command in `docs/contracts/README.md`
- [ ] T003 [P] Configure Spotless/Checkstyle, Error Prone, and formatter rules matching constitution guardrails
- [ ] T004 [P] Set up Git hooks/CI jobs that block merges when constitution checks fail, including `test`, `bddTest`, and `contractTest` gates
- [ ] T005 [P] Add Springdoc OpenAPI + Swagger UI dependencies, seed `/swagger-ui` route, and disable Try-It-Out by default
- [ ] T006 Define API key header (`X-SmartLetter-Api-Key`), add sample property placeholders, and document rotation procedures in `docs/security/api-keys.md`
- [ ] T007 [P] Install and verify the baseline testing toolchain (JUnit 5, AssertJ, Mockito, Spring Cloud Contract, Testcontainers, Cucumber JVM) with Maven goals (`./mvnw test`, `./mvnw verify -Pcucumber`) and document how to run them before implementation starts
- [ ] T008 [P] Initialize `/infra/terraform` (or `/infra/pulumi`) modules covering Cloud Run, Artifact Registry, Secret Manager, IAM, and monitoring resources; configure remote/state storage and module README.
- [ ] T009 [P] Add CI automation that runs `terraform fmt`, `terraform validate`, and `terraform plan` (or Pulumi preview) on every PR touching `/infra/`, attaching the plan artifact to the review.
- [ ] T010 [P] Author `.github/workflows/ci.yml` that runs lint + TDD/BDD/contract suites, coverage gates, Terraform plan, container build+scan, SBOM upload, and auto-deploy to the test Cloud Run service when all checks pass.
- [ ] T011 [P] Author `.github/workflows/deploy-prod.yml` (or add release job) that triggers on merges to the protected branch, reruns the quality gates, requires green test workflow status, deploys to the production Cloud Run service, and publishes the annotated SemVer tag for the release.
- [ ] T012 Configure workflow secrets (API keys, GCP service accounts) via GitHub Environments with required reviewers for production deploys and document rotation procedures.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**:warning: CRITICAL**: No user story work can begin until this phase is complete

Examples of foundational tasks (adjust based on your project):

- [ ] T013 Create shared DTOs, validation annotations, and exception mappers for `POST /letters`
- [ ] T014 [P] Implement shared `SmartLetterLlmClient` with WebClient, timeouts, retry/backoff, and prompt redaction utilities
- [ ] T015 Configure email templating directories (`src/main/resources/templates/`) plus Thymeleaf + sanitizer configuration
- [ ] T016 Configure Spring Mail provider credentials, provider-specific headers, and health probes
- [ ] T017 Add Micrometer metrics, tracing filters, and log correlation for request -> LLM -> email path
- [ ] T018 Define deterministic fallback email content and store snapshots under `src/test/resources/templates/__snapshots__/`
- [ ] T019 Setup environment configuration management (Spring Config + secrets manager bindings)
- [ ] T020 [P] Configure Paketo Buildpacks or Jib settings (including SBOM generation) and ensure the resulting image runs as non-root
- [ ] T021 [P] Create Artifact Registry repository, `infra/cloudrun/service.yaml`, and `scripts/deploy-cloudrun.sh` with Always Free tier limits baked in
- [ ] T022 [P] Implement API key authentication filter/interceptor plus constant-time comparison utility in `src/main/java/.../security`
- [ ] T023 Wire API key storage via Cloud Secret Manager + Spring Config, add rotation cron/runbook, and emit audit logs for auth successes/failures
- [ ] T024 [P] Secure `/swagger-ui` with IAP or Basic Auth, document QA credentials, and wire audit logging for usage
- [ ] T025 Add automated check ensuring the deployed OpenAPI JSON matches the generated `docs/contracts/openapi.yaml` and is linked from Swagger UI
- [ ] T026 [P] Create shared Cucumber JVM assets (`src/test/resources/features`, glue packages), seed sample feature mapping to US1, and document naming conventions
- [ ] T027 Wire Spring Cloud Contract + Testcontainers base classes into CI so `contractTest` and `bddTest` fail the build when scenarios are missing or out-of-date
- [ ] T028 [P] Add Cloud Firestore (Datastore mode) emulator dependencies, Maven profiles/goals, and docker-compose entry so integration tests can run offline; document how to seed data before each suite.
- [ ] T029 Implement `AppSettingRepository` + caching layer under `src/main/java/.../settings/`, including optimistic locking/version stamping and Micrometer metrics for reads/writes.
- [ ] T030 [P] Create IaC + migration scripts (Terraform seeding, JSON fixtures, or Spring Boot runner) that provision default application settings while keeping quotas within Always Free limits (<= 1 GB storage, <= 50k reads/day).
- [ ] T031 Wire GitHub Actions environments (`test`, `production`) to Cloud Run service accounts, configure required approvals for production, and document environment protection rules.

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - [Title] (Priority: P1) :dart: MVP

**Goal**: [Brief description of what this story delivers]

**Independent Test**: [How to verify this story works on its own]

### Tests for User Story 1 (MANDATORY - fail-first) :warning:

> **NOTE: Capture GIVEN/WHEN/THEN in feature files and make sure every test fails before implementation begins.**

- [ ] T040 [P] [US1] Author/update Cucumber JVM acceptance scenarios plus glue code under `src/test/resources/features/us1/*.feature`
- [ ] T041 [P] [US1] Contract test for `POST /letters` using MockMvc + OpenAPI validator in `src/test/java/.../contract/LetterContractTest.java`
- [ ] T042 [P] [US1] Snapshot test for HTML + plaintext templates in `src/test/java/.../templates/LetterTemplateSnapshotTest.java`
- [ ] T043 [US1] Integration test covering request -> LLM stub -> email fallback in `src/test/java/.../integration/LetterFlowIT.java`

### Implementation for User Story 1

- [ ] T044 [P] [US1] Implement `LetterRequest`/`LetterResponse` DTOs with validation in `src/main/java/.../api/`
- [ ] T045 [P] [US1] Build orchestrator service `LetterService` combining LLM output + fallback template
- [ ] T046 [US1] Implement `LetterController` with OpenAPI annotations and contract tests
- [ ] T047 [US1] Add Micrometer timers and structured logging for correlation IDs across controller/service/client layers
- [ ] T048 [US1] Record fallback incidents to persistent audit store (if enabled)

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - [Title] (Priority: P2)

**Goal**: [Brief description of what this story delivers]

**Independent Test**: [How to verify this story works on its own]

### Tests for User Story 2 (MANDATORY - fail-first) :warning:

- [ ] T050 [P] [US2] Author/update BDD scenarios for locale/tone branching and map them to glue in `src/test/java/.../bdd`
- [ ] T051 [P] [US2] Contract test for new request fields/version headers in `src/test/java/.../contract/LetterContractV2Test.java`
- [ ] T052 [P] [US2] Rendering regression test for new template variant (locale, tone) in `src/test/java/.../templates/...`
- [ ] T053 [US2] Integration test simulating LLM retry/exponential backoff vs provider throttling

### Implementation for User Story 2

- [ ] T054 [P] [US2] Extend prompt builder to support locale/tone switches in `src/main/java/.../llm/PromptBuilder.java`
- [ ] T055 [US2] Update email renderer to select correct template pair and ensure accessibility annotations
- [ ] T056 [US2] Wire API version negotiation or feature flags while keeping v1 contract intact
- [ ] T057 [US2] Update metrics/alerts for the new variant-specific KPIs

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: User Story 3 - [Title] (Priority: P3)

**Goal**: [Brief description of what this story delivers]

**Independent Test**: [How to verify this story works on its own]

### Tests for User Story 3 (MANDATORY - fail-first) :warning:

- [ ] T060 [P] [US3] Define BDD scenarios covering metadata/attachment permutations and glue them to queue/escalation behaviors
- [ ] T061 [P] [US3] Contract test for new metadata/attachments (if any) using WireMock for downstream dependencies
- [ ] T062 [P] [US3] Load test scenario for throughput/latency budgets using Gatling or k6
- [ ] T063 [US3] Integration test for fallback escalation path (e.g., queueing manual review)

### Implementation for User Story 3

- [ ] T064 [P] [US3] Introduce new domain model (e.g., Attachment, ChannelPreference) with validation + persistence if required
- [ ] T065 [US3] Extend orchestrator to branch logic (LLM vs manual template) based on policy engine result
- [ ] T066 [US3] Implement supporting scheduler/queue integration if escalations need async handling
- [ ] T067 [US3] Update observability dashboards and alerts for new flow variants

**Checkpoint**: All user stories should now be independently functional

---

[Add more user story phases as needed, following the same pattern]

---

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] TXXX [P] Documentation updates in `docs/` (OpenAPI changelog, prompt catalog, email template index)
- [ ] TXXX Code cleanup, dead template removal, and prompt refactors
- [ ] TXXX Performance/latency tuning across LLM + SMTP integrations
- [ ] TXXX [P] Additional unit tests or snapshot refreshes in `src/test/java` and `src/test/resources`
- [ ] TXXX Security hardening (secret rotation, prompt redaction review)
- [ ] TXXX Run quickstart.md validation plus staging send-down audit
- [ ] TXXX Execute `scripts/deploy-cloudrun.sh` dry run, verify Cloud Run revision stays within Always Free tier limits, and capture rollout notes
- [ ] TXXX Validate Swagger UI (auth, Try-It-Out policy, OpenAPI hash) in staging/production and attach manual test evidence
- [ ] TXXX Audit API key rotation logs, revoke unused keys, and document evidence for the release
- [ ] TXXX Capture links to the latest GitHub Actions push (test deploy) and release (production deploy) workflow runs, ensuring all required jobs succeeded and artifacts (tests, coverage, Terraform plan, SBOM) are archived.
- [ ] TXXX Record the annotated SemVer tag (`vMAJOR.MINOR.PATCH`) produced for the release, including the workflow run URL in the tag annotation and release notes evidence.
- [ ] TXXX Capture Firestore usage metrics (storage, daily reads/writes) and attach screenshots/logs proving the service remains within Always Free limits; document any migrations applied this release.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User stories can then proceed in parallel (if staffed)
  - Or sequentially in priority order (P1 -> P2 -> P3)
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P2)**: Can start after Foundational (Phase 2) - May integrate with US1 but should be independently testable
- **User Story 3 (P3)**: Can start after Foundational (Phase 2) - May integrate with US1/US2 but should be independently testable

### Within Each User Story

- Tests (if included) MUST be written and FAIL before implementation
- Models before services
- Services before endpoints
- Core implementation before integration
- Story complete before moving to next priority
- Honor code-first OpenAPI generation, LLM safety, rich-email integrity, containerization/GCP, Swagger UI exposure, API key guardrails, and INVEST constraints for every change; document exceptions in the Complexity Tracking log
- Keep WIP low: no engineer should own more than two concurrent stories; finish INVEST slices before starting new ones and capture iteration demos in docs/specs

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel
- All Foundational tasks marked [P] can run in parallel (within Phase 2)
- Once Foundational phase completes, all user stories can start in parallel (if team capacity allows)
- All tests for a user story marked [P] can run in parallel
- Models within a story marked [P] can run in parallel
- Different user stories can be worked on in parallel by different team members

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together (if tests requested):
Task: "Contract test for [endpoint] in tests/contract/test_[name].py"
Task: "Integration test for [user journey] in tests/integration/test_[name].py"

# Launch all models for User Story 1 together:
Task: "Create [Entity1] model in src/models/[entity1].py"
Task: "Create [Entity2] model in src/models/[entity2].py"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Test User Story 1 independently
5. Deploy/demo if ready

### Incremental Delivery

1. Complete Setup + Foundational -> Foundation ready
2. Add User Story 1 -> Test independently -> Deploy/Demo (MVP!)
3. Add User Story 2 -> Test independently -> Deploy/Demo
4. Add User Story 3 -> Test independently -> Deploy/Demo
5. Each story adds value without breaking previous stories

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: User Story 1
   - Developer B: User Story 2
   - Developer C: User Story 3
3. Stories complete and integrate independently

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify tests fail before implementing
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence
