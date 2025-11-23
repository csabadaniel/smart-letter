# Feature Specification: [FEATURE NAME]

**Feature Branch**: `[###-feature-name]`  
**Created**: [DATE]  
**Status**: Draft  
**Input**: User description: "$ARGUMENTS"

## User Scenarios & Testing *(mandatory)*

<!--
  IMPORTANT: User stories should be PRIORITIZED as user journeys ordered by importance.
  Each user story/journey must be INDEPENDENTLY TESTABLE - meaning if you implement just ONE of them,
  you should still have a viable MVP (Minimum Viable Product) that delivers value.
  
  Assign priorities (P1, P2, P3, etc.) to each story, where P1 is the most critical.
  Think of each story as a standalone slice of functionality that can be:
  - Developed independently
  - Tested independently
  - Deployed independently
  - Demonstrated to users independently
-->

> Constitution alignment: at least one P1 story must exercise the full request -> LLM -> email pipeline, document the fallback narrative, and state how accessibility + observability requirements are verified. Every story you capture MUST meet INVEST (Independent, Negotiable, Valuable, Estimable, Small, Testable) so it can ship on its own, and each acceptance criterion must translate directly into executable Gherkin scenarios (BDD) plus TDD unit/contract tests.

### User Story 1 - [Brief Title] (Priority: P1)

[Describe this user journey in plain language]

**Why this priority**: [Explain the value and why it has this priority level]

**Independent Test**: [Describe how this can be tested independently - e.g., "Can be fully tested by [specific action] and delivers [specific value]"]

**Acceptance Scenarios**:

1. **Given** [initial state], **When** [action], **Then** [expected outcome]
2. **Given** [initial state], **When** [action], **Then** [expected outcome]

---

### User Story 2 - [Brief Title] (Priority: P2)

[Describe this user journey in plain language]

**Why this priority**: [Explain the value and why it has this priority level]

**Independent Test**: [Describe how this can be tested independently]

**Acceptance Scenarios**:

1. **Given** [initial state], **When** [action], **Then** [expected outcome]

---

### User Story 3 - [Brief Title] (Priority: P3)

[Describe this user journey in plain language]

**Why this priority**: [Explain the value and why it has this priority level]

**Independent Test**: [Describe how this can be tested independently]

**Acceptance Scenarios**:

1. **Given** [initial state], **When** [action], **Then** [expected outcome]

---

[Add more user stories as needed, each with an assigned priority]

### Edge Cases

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right edge cases.
-->

- What happens when the LLM endpoint times out, redacts the response, or returns content that violates safety policies?
- How does the system handle email provider failures, template rendering errors, or missing personalization fields while still honoring fallback delivery?
- What is the behavior when duplicate requests (same correlation ID) arrive or when validation fails for inputs coming from upstream services?
- How does the service respond when Cloud Run cold starts exceed latency budgets or Always Free quotas (requests, CPU, memory) are exhausted mid-flight?
- What happens if container build/push automation fails or a `gcloud run deploy` rollback is required?
- How is Swagger UI protected in production, and what happens when Try-It-Out is disabled or when the OpenAPI doc is out of sync with the deployed version?
- How are API keys provisioned, rotated, revoked, and prevented from leaking (e.g., logging, Swagger UI inputs, browser storage)?
- What is the recovery path if GitHub Actions pipelines fail mid-deploy, gates time out, or a commit triggers multiple overlapping test deployments?
- How does the Firestore-backed settings store behave when quotas (reads/writes/storage) approach the Always Free limits or when concurrent updates conflict?

## Requirements *(mandatory)*

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right functional requirements.
-->

### Functional Requirements

- **FR-001**: Service MUST expose versioned REST endpoints (e.g., `POST /letters`) exactly as defined in `docs/contracts/openapi.yaml`.
- **FR-002**: All inbound DTOs MUST pass `jakarta.validation` rules (email syntax, personalization tokens, locale) before execution continues.
- **FR-003**: The orchestrator MUST invoke the LLM client via the shared `WebClient`, including retry/timeout budgets declared in the plan.
- **FR-004**: LLM output MUST be sanitized, truncated to policy limits, and merged into approved templates without raw string concatenation.
- **FR-005**: Emails MUST be sent with both HTML and plaintext bodies plus accessibility-compliant structure; tests must snapshot both.
- **FR-006**: The system MUST emit Micrometer metrics/logs for correlation IDs, LLM latency, email-send outcomes, and fallback activations.
- **FR-007**: CI/CD MUST build an OCI image using Paketo Buildpacks or Jib, generate an SBOM, and push the artifact to Artifact Registry with vulnerability scanning enforced.
- **FR-008**: Deployments MUST target Cloud Run with Always Free settings (<= 1 vCPU, <= 256 MiB memory, <= 20 concurrency) and document the exact `gcloud run deploy` or Terraform invocation.
- **FR-009**: The service MUST authenticate every request via `X-SmartLetter-Api-Key`, enforcing 32+ byte entropy, constant-time comparisons, per-key rate limits, and rotation automation backed by Cloud Secret Manager.
- **FR-010**: Swagger UI MUST be deployed in every environment, source the same `docs/contracts/openapi.yaml`, prompt users for an API key (never storing it), and apply the same backend authentication pipeline when Try-It-Out is enabled.
- **FR-011**: Implementation MUST follow TDD—write failing JUnit/AssertJ (and when applicable Spring Cloud Contract/Testcontainers) tests before production code, keep coverage >= 90% on changed files, and document the red -> green -> refactor cycle per story.
- **FR-012**: Each story MUST add at least one executable BDD scenario (Gherkin via Cucumber JVM) tagged with the story ID and runnable in CI; scenarios must mirror the acceptance criteria verbatim.
- **FR-013**: All infrastructure resources (Cloud Run, Artifact Registry, Secret Manager, IAM, monitoring) MUST be managed via code stored under `/infra/` (Terraform, Pulumi, or scripted `gcloud`). Manual console edits require retroactive IaC updates in the same iteration.
- **FR-014**: Permanent application settings MUST persist in Cloud Firestore (Datastore mode) collections defined in IaC, stay within Always Free quotas (<= 1 GB storage, <= 50k document reads/day, <= 20k writes/day), and be accessed through typed repositories with optimistic concurrency and audit logging.
- **FR-015**: GitHub Actions workflows must run on every commit (test deploy) and on merges to the release branch (production deploy), executing the full quality gate (lint, unit/integration/contract/BDD suites, coverage, Terraform plan, container build + scan) before invoking deployment jobs. Workflows must publish artifacts and tag Cloud Run revisions with the source commit SHA.
- **FR-016**: All documentation artifacts generated or updated for this feature (plan, spec, tasks, checklists, runbooks) MUST remain ASCII-only. Use GitHub Markdown emoji codes (e.g., `:warning:`) for expressive icons, and document the lint/scan that proves compliance before merge.

*Example of marking unclear requirements:*

- **FR-017**: System MUST deliver email through [NEEDS CLARIFICATION: provider not specified - SES, Postmark, SMTP relay?]
- **FR-018**: LLM prompt instructions MUST support [NEEDS CLARIFICATION: languages/tones not defined]

### LLM & Email Safeguards *(constitution-required)*

- Describe the deterministic fallback content, who approves it, and where it lives in the repo.
- Define the redaction/sanitization rules applied before prompts leave the service (PII fields, API keys, secrets).
- Document how snapshot tests store golden HTML/text fixtures and how regressions will be reviewed.
- Outline alert thresholds for `llm.latency`, `email.sent`, and `email.fallback_triggered` metrics.

### Containerization & GCP Deployment *(constitution-required)*

- Describe how the OCI image is produced (Buildpacks vs. Jib), tagged, and promoted between environments.
- Document Artifact Registry repositories, SBOM storage, and vulnerability scanning expectations.
- Specify Cloud Run service configuration (region, vCPU, memory, concurrency, min/max instances) and how it remains inside the Always Free tier.
- Capture the exact `gcloud run deploy` or infrastructure-as-code commands, including environment variables and Secret Manager references.
- Reference the `/infra/` modules or scripts (Terraform/Pulumi) that provision these resources, note how state is stored (e.g., Terraform Cloud, GCS bucket), and include links to `plan`/`apply` artifacts required for review.
- Define rollback/blue-green strategy and how cold-start latency will be measured and enforced.

### Persistent Settings Store *(constitution-required)*

- Identify each Firestore collection/kind used for permanent application settings, the document schema, and default values.
- Explain how changes to settings are migrated (seed scripts, IaC fixtures, background jobs) and how to roll back without exceeding Always Free limits.
- Document caching strategy, TTLs, and how stale data is detected; describe optimistic locking/version fields that prevent overwrites.
- Specify how integration tests leverage the Firestore emulator, what seed data they require, and how CI cleans up between runs.

### Continuous Delivery Automation *(constitution-required)*

- Reference the GitHub Actions workflows that run on push (test deploy) and merge (production deploy), including job names, secrets, and artifact outputs.
- Detail the quality gates (tests, coverage minimums, Terraform plan diffs, SBOM, container scan) and how failing gates block the deploy steps.
- Describe how workflow runs annotate Cloud Run revisions with commit SHAs, publish environment URLs, and notify stakeholders (Slack/email) when deployments complete or fail.
- Outline rollback/roll-forward expectations and manual intervention steps when test or production deployments require remediation.

### Access Control & API Keys *(constitution-required)*

- Document the API key header name, minimum length/entropy, and lifecycle (creation, rotation, revocation) per environment.
- Specify how keys are stored (Cloud Secret Manager) and injected into Cloud Run, plus audit logging strategy for authentication success/failure.
- Describe rate limiting/quota rules per key and how breaches trigger alerts.
- Explain how Swagger UI collects the key from the user without persisting it (session storage, clipboard) and verifies that Try-It-Out uses the same header.

### Testing Discipline (TDD + BDD) *(constitution-required)*

- Outline the TDD plan: which classes/tests will fail first, how red -> green -> refactor will be demonstrated, and coverage targets per layer.
- Document BDD scenarios in Gherkin (Given/When/Then) tied to INVEST stories; specify tags, data fixtures, and how scenarios are executed with Cucumber JVM (e.g., `mvn verify -Pcucumber`).
- Note any Spring Cloud Contract stubs or Testcontainers environments needed to satisfy the scenario without real upstream dependencies.
- Describe how CI enforces these suites (parallelization, expected runtime budgets, required artifacts/logs).

### Key Entities *(include if feature involves data)*

- **LetterRequest**: Canonical inbound payload containing recipient metadata, personalization tokens, locale, and requested letter type.
- **PromptContext**: Structured object that assembles system prompt, user content, and safety filters before hitting the LLM endpoint.
- **EmailRender**: Resulting HTML + plaintext body pair, including accessibility metadata, footer requirements, and links tracked for auditing.
- **DeployTarget**: Cloud Run configuration object (service name, region, concurrency, env vars, Secret Manager bindings) consumed by deployment scripts.
- **ApiKeyPolicy**: Defines key format, rotation cadence, allowed scopes/environments, and rate-limit metadata referenced by authentication middleware.
- **SwaggerEndpoint**: Captures route, auth scheme, allowed users, Try-It-Out policy, and linkage to the OpenAPI artifact per environment.
- **TestSuiteDefinition**: Maps each INVEST story to the JUnit/AssertJ unit tests, Spring Cloud Contract/Testcontainers fixtures, and Cucumber JVM scenarios that prove it works.
- **AppSetting**: Firestore document describing immutable key, default value, effective value, version stamp, and audit metadata controlling application-wide behavior.
- **DeploymentWorkflow**: Describes the GitHub Actions definition (file, trigger, jobs), required gates, artifacts, target environment (test or production), and notification hooks.

## Success Criteria *(mandatory)*

<!--
  ACTION REQUIRED: Define measurable success criteria.
  These must be technology-agnostic and measurable.
-->

### Measurable Outcomes

- **SC-001**: 95% of requests produce a delivered email (HTML + plaintext) within 5 seconds end-to-end.
- **SC-002**: LLM fallback rate stays below 1% per hour in staging and production.
- **SC-003**: Accessibility/linting suite passes 100% of email snapshots before release.
- **SC-004**: Observability dashboards capture correlation IDs for 100% of letters and allow tracing from request to SMTP provider ID.
- **SC-005**: Container image build + scan completes under 5 minutes and yields zero critical vulnerabilities before deployment.
- **SC-006**: Cloud Run revisions stay within Always Free quotas (<= 2M requests/month, <= 360k vCPU-seconds) while keeping cold-start latency < 2 seconds and steady-state latency < 1 second p95.
- **SC-007**: Swagger UI is reachable in staging and production with current OpenAPI docs, enforced auth, and recorded manual test evidence per release.
- **SC-008**: 100% of API keys rotate within the mandated window (<= 90 days) with audit logs demonstrating issuance, rotation, and revocation events.
- **SC-009**: Every delivered iteration ships at least one INVEST-compliant story with all acceptance tests automated or documented, and no in-flight story remains open for more than two iterations.
- **SC-010**: 100% of new code is covered by tests authored via TDD (>=90% coverage on changed files) and each INVEST story has at least one passing Cucumber JVM scenario recorded in CI artifacts for the release.
- **SC-011**: 100% of infrastructure changes execute through repository IaC modules with `plan` artifacts attached to PRs and zero manual console drift at release sign-off.
- **SC-012**: Firestore usage for permanent settings stays within Always Free quotas (<= 1 GB storage, <= 50k document reads/day), with automated alerts when thresholds exceed 80%.
- **SC-013**: 100% of commits complete the GitHub Actions push workflow (quality gates + test deploy) successfully, and 100% of merges to the release branch complete the production deploy workflow with links captured in release notes.
