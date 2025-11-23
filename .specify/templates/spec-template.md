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

> Constitution alignment: at least one P1 story must exercise the full request → LLM → email pipeline, document the fallback narrative, and state how accessibility + observability requirements are verified.

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
- **FR-008**: Deployments MUST target Cloud Run with Always Free settings (≤1 vCPU, ≤256 MiB memory, ≤20 concurrency) and document the exact `gcloud run deploy` or Terraform invocation.
- **FR-009**: Swagger UI MUST be deployed in every environment, sourced from the same `docs/contracts/openapi.yaml`, protected via identity-aware proxy or Basic Auth with audit logging, and include explicit Try-It-Out policies for production.

*Example of marking unclear requirements:*

- **FR-010**: System MUST deliver email through [NEEDS CLARIFICATION: provider not specified - SES, Postmark, SMTP relay?]
- **FR-011**: LLM prompt instructions MUST support [NEEDS CLARIFICATION: languages/tones not defined]

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
- Define rollback/blue-green strategy and how cold-start latency will be measured and enforced.

### Key Entities *(include if feature involves data)*

- **LetterRequest**: Canonical inbound payload containing recipient metadata, personalization tokens, locale, and requested letter type.
- **PromptContext**: Structured object that assembles system prompt, user content, and safety filters before hitting the LLM endpoint.
- **EmailRender**: Resulting HTML + plaintext body pair, including accessibility metadata, footer requirements, and links tracked for auditing.
- **DeployTarget**: Cloud Run configuration object (service name, region, concurrency, env vars, Secret Manager bindings) consumed by deployment scripts.
- **SwaggerEndpoint**: Captures route, auth scheme, allowed users, Try-It-Out policy, and linkage to the OpenAPI artifact per environment.

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
- **SC-006**: Cloud Run revisions stay within Always Free quotas (≤2M requests/month, ≤360k vCPU-seconds) while keeping cold-start latency < 2 seconds and steady-state latency < 1 second p95.
- **SC-007**: Swagger UI is reachable in staging and production with current OpenAPI docs, enforced auth, and recorded manual test evidence per release.
