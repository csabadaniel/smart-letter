<!--
Sync Impact Report
Version: 1.8.0 → 1.9.0
Modified Principles:
- Service Guardrails: Continuous delivery now must flow through GitHub Actions pipelines per environment with Always Free quota enforcement; persistent settings and GCP deployment quotas clarified with ASCII limits
- Workflow & Quality Gates: Added continuous delivery evidence gate plus documentation encoding discipline mandating ASCII text with GitHub Markdown emoji codes only
Added Sections:
- None
Removed Sections:
- None
Templates:
- .specify/templates/plan-template.md :white_check_mark: updated
- .specify/templates/spec-template.md :white_check_mark: updated
- .specify/templates/tasks-template.md :white_check_mark: updated
Follow-ups:
- None
-->

# Smart Letter Constitution

## Core Principles

### I. Contract-First Interfaces
- Publish OpenAPI definitions and example payloads under `docs/contracts/openapi.yaml` before implementing any controller or client.
- Enforce `jakarta.validation` and explicit error contracts on every DTO so invalid data is rejected deterministically and logged with correlation IDs.
- Introduce new response fields in a backward compatible way; breaking changes require versioned routes or header-based negotiation plus a documented migration window.
*Rationale*: Stable contracts keep downstream systems predictable and minimize regression risk in this single-purpose microservice.

### II. LLM Safety Envelope
- Route all prompt/response traffic through a single Spring `WebClient` that applies strict timeouts, retry/backoff rules, request/response size caps, and prompt redaction for secrets or PII before the payload leaves the service boundary.
- Persist hashed prompt metadata and model decisions in audit logs so production issues can be replayed without exposing user data.
- Provide deterministic fallbacks: if the LLM call fails or violates policy, return a templated human-authored email body and flag the incident via metrics/alerts instead of delivering unreviewed content.
*Rationale*: Guardrails keep AI output safe, support compliance, and avoid user-facing failures from an upstream dependency we do not control.

### III. Rich Email Integrity
- Generate all outbound emails through vetted templates (Thymeleaf or equivalent) that emit both HTML and plaintext parts; never concatenate raw strings from model output directly into SMTP payloads.
- Enforce accessibility (semantic headings, meaningful alt text) and sanitize the final markup before sending; snapshot tests must detect unintended markup changes.
- Validate deliverability by linting links, inline CSS, and required footer content; block deployment if rendering tests fail.
*Rationale*: The email is the only user-visible surface, so high-fidelity rendering and safety gates are mandatory.

### IV. Test-Driven & Behavior-Driven Delivery
- Every change starts with failing unit and contract tests written in JUnit 5 + AssertJ (for services/controllers) and Spring Cloud Contract or WireMock stubs (for integrations). Production code cannot be authored before the corresponding test exists and fails (Red → Green → Refactor cycle).
- Acceptance criteria are captured as executable Gherkin scenarios using Cucumber JVM that exercise end-to-end flows—including prompt building, LLM call, email rendering, and fallback logic. Each user story must contribute at least one new or updated scenario.
- Regression safety requires Testcontainers-backed integration suites for external dependencies (LLM mock, SMTP provider) plus deterministic snapshots for HTML output. Test suites run in CI on every PR and must complete in <10 minutes.
*Rationale*: TDD + BDD keep the service verifiable, encourage incremental delivery, and document system behavior in a user-centric language.

### V. Infrastructure as Code Stewardship
- All infrastructure for Smart Letter (Cloud Run services, Artifact Registry repos, Secret Manager bindings, monitoring dashboards, IAM policies, scheduled jobs) must be declared as code stored inside this repository under `infra/` (Terraform, Pulumi, or declarative `gcloud` wrappers). No manual console drifts are allowed.
- Every infra change requires automated validation (`terraform fmt`, `terraform validate`, `terraform plan` or equivalent) in CI prior to merge, and plans must be attached to PRs for review.
- Deployments run through codified workflows (e.g., `terraform apply`, staged scripts) that emit change logs and tie revisions back to Git SHAs; emergency fixes must still land as commits shortly after execution.
*Rationale*: Treating infrastructure as code guarantees reproducibility, auditability, and parity between environments, which is critical for a cost-sensitive Cloud Run deployment.

## Service Guardrails

- **Runtime Stack**: Java 21, Spring Boot 3.3.x, Gradle build, Spring MVC controllers, Spring WebClient for outbound HTTP, and Spring Mail/Jakarta Mail for SMTP. Deviations require architecture approval.
- **LLM Integration**: Use HTTPS JSON APIs with API-key auth stored in the secrets manager; prompts live in `src/main/resources/prompts/` and must be versioned.
- **Email Delivery**: Store templates in `src/main/resources/templates/` with paired HTML/text variants, and send via a provider that supports rich text (e.g., SES, Postmark). Capture provider message IDs for traceability.
- **Configuration & Secrets**: Manage via Spring Config + environment variables; never persist raw LLM responses beyond transient processing logs.
- **Persistent Settings Store**: Permanent application settings (feature toggles, personalization defaults, provider knobs) must live in Cloud Firestore (Datastore mode) configured to remain within the Always Free tier (<= 1 GB storage, <= 50k reads/day). Access them through a dedicated repository/service layer (`AppSettingStore`) that enforces read caching, optimistic concurrency, and change auditing. IaC must provision collections/indexes; no manual console edits.
- **Observability**: Emit Micrometer metrics (`llm.latency`, `email.sent`, `email.fallback_triggered`) and structured logs with trace IDs propagated through LLM and SMTP calls. Alerts fire when fallback rates exceed 1% per hour.
- **Containerization & Artifacts**: Every build must produce a reproducible OCI image using Paketo Buildpacks or Jib, pinned to distroless or Alpine base images with non-root users. Images are published to GCP Artifact Registry with SBOM metadata and vulnerability scanning enabled.
- **GCP Deployment**: The service runs on Cloud Run (regional) configured to stay within the Always Free tier (<= 1 vCPU, <= 256 MiB memory, 2M requests/month). Set max concurrency <= 20, request timeout <= 60s, and CPU allocation `on-demand` to honor the cost envelope. Use Cloud Secret Manager for API keys and Cloud Logging/Monitoring for runtime telemetry. Persistent state must not rely on writable container filesystem; use GCP managed services instead.
- **Swagger UI Exposure**: Host Swagger UI via Springdoc at `/swagger-ui` (or equivalent) for every environment. Production/staging endpoints must sit behind identity-aware proxy or Basic Auth with per-user audit logging, and the published OpenAPI doc must match the deployed version. Disable Try-It-Out in production unless routed through a dedicated QA service account.
- **API Key Access Control**: All inbound traffic MUST present a 32+ byte API key in the `X-SmartLetter-Api-Key` header. Keys are generated by the platform team, stored only in Cloud Secret Manager, rotated at least every 90 days, and scoped per environment. Authentication middleware must enforce constant-time comparison, per-key rate limits, and structured audit logs. Swagger UI Try-It-Out may send the API key via the same header once the user enters it through a secure input control—no keys baked into the frontend.
- **Infrastructure as Code**: Terraform/Pulumi modules and shell automation stored under `infra/` describe Cloud Run, Artifact Registry, Secret Manager, IAM bindings, and monitoring assets. Manual configuration changes are prohibited unless captured as follow-up commits. Every release must reference the exact IaC module version applied.
- **Continuous Delivery Pipelines**: Maintain separate **test** (staging) and **production** environments. GitHub Actions workflows must (1) on every commit to any feature branch run the full quality gate (lint, TDD, BDD, contracts, IaC validation, container build) and, when successful, deploy automatically to the test environment; (2) on merges into the protected release branch (e.g., `main`) rerun the gates and deploy to production. Deployments are blocked if any gate fails or if Firestore/Cloud Run quota checks would be exceeded. Every workflow publishes artifacts (JUnit, Cucumber, Terraform plan, container digest) and tags the deployed Cloud Run revision with the commit SHA.

## Workflow & Quality Gates

- **Definition of Ready**: A feature cannot enter implementation until the OpenAPI delta, prompt contract, and email template outline are documented, along with acceptance tests for success/failure paths.
- **Testing Expectations**: Follow strict TDD: write/commit failing JUnit 5 tests (with AssertJ + Mockito) before implementation, then add code until tests pass. BDD suites (Cucumber JVM) must express each INVEST story’s acceptance criteria in Gherkin, run in CI, and stay green. Contract tests (Spring Cloud Contract), Testcontainers integrations, and template snapshots are non-optional for touched components.
- **Container Readiness**: Before coding begins, teams must document container resource budgets, Cloud Run service settings (region, concurrency, memory), and how the change preserves Always Free limits. Every feature plan must include build/push automation steps.
- **Swagger Availability**: Definition of Ready also requires confirming the Swagger UI route, auth mechanism, and sample credentials for QA; all new/changed endpoints must be manually exercised through Swagger before code review completes.
- **INVEST User Stories**: Every feature spec and plan must decompose work into INVEST-compliant stories (Independent, Negotiable, Valuable, Estimable, Small, Testable). Stories must articulate measurable acceptance criteria and can be shipped incrementally without blocking others.
- **Incremental Delivery Cadence**: Work proceeds in short iterations. Each iteration targets the smallest shippable story, updates docs/tasks to reflect status, and must be demonstrable through Swagger or automated tests. Backlog stories stay prioritized; WIP limits prevent more than two simultaneous stories per engineer.
- **Code Review Checklist**: PRs must cite which principle they satisfy, attach contract diffs, and show evidence that fallbacks, validation, accessibility checks, containerization/GCP deployment updates, and API key handling are covered. No merge if any checklist item is unresolved.
- **Infrastructure as Code Evidence**: Definition of Ready and PR reviews must reference the IaC module paths being touched, include `terraform plan` (or equivalent) output, and describe how state is managed per environment. Any manual change requires a documented remediation plan to bring IaC back in sync within 24 hours.
- **Settings Governance**: Each feature plan/spec must declare whether it introduces or updates persistent settings. Implementation MUST include migration scripts or IaC updates for Firestore documents, automated tests proving defaults, and runbooks describing how to roll back values without leaving free tier limits.
- **Documentation Encoding Discipline**: All documentation, templates, prompts, and governance artifacts within this repo must remain ASCII-only. Expressive symbols are permitted only via GitHub Markdown emoji codes (e.g., `:warning:`). Every PR updating docs must cite the lint/check used to enforce encoding and reviewers must block merges containing stray Unicode outside code samples.
- **Continuous Delivery Evidence**: Plans/specs/tasks must reference the GitHub Actions workflow IDs that will run for the feature, define how commit builds promote to the test environment, and show how merges to the release branch promote to production. Required artifacts (test reports, coverage summaries, Terraform plan output, SBOM, Cloud Run revision ID) must be archived per run; deployments cannot proceed until the workflow marks the quality gates as passed.
- **Release Gate**: A release candidate must pass container image scans, complete a successful GitHub Actions-driven deploy to the test environment (triggered by the final commit) and a production deploy (triggered by merging), demonstrate zero critical alerts in staging for 24 hours, produce a sample outbound email approved by product/UX, expose the matching Swagger UI with the latest OpenAPI schema accessible to QA via API key authentication, and deliver proof that all BDD scenarios and TDD suites ran green on the release candidate. Release notes must link to the IaC change set applied, the CI/CD workflow run URLs, and include evidence that Firestore settings were migrated via code.

## Governance

This constitution supersedes other development practices for the Smart Letter service. Amendments require an RFC describing the motivation, risk assessment, and migration plan, plus approval from the service tech lead and product owner. Version changes follow semantic rules: MAJOR for removals or redefinitions of principles/sections, MINOR for new principles or expanded guardrails, PATCH for clarifications that do not change obligations. Every merged feature plan/spec/tasks document must include a "Constitution Check" section that records compliance evidence. PRs without a completed "Constitution Check" section must be rejected during review. The release engineer schedules quarterly compliance reviews; any violations must be remediated before the next release or explicitly waived with documented risk acceptance.

**Version**: 1.9.0 | **Ratified**: 2025-11-22 | **Last Amended**: 2025-11-23
