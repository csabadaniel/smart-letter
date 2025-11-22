<!--
Sync Impact Report
Version: N/A → 1.0.0
Modified Principles:
- None (initial publication)
Added Sections:
- Core Principles
- Service Guardrails
- Workflow & Quality Gates
- Governance
Removed Sections:
- None
Templates:
- .specify/templates/plan-template.md ✅ updated
- .specify/templates/spec-template.md ✅ updated
- .specify/templates/tasks-template.md ✅ updated
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

## Service Guardrails

- **Runtime Stack**: Java 21, Spring Boot 3.3.x, Gradle build, Spring MVC controllers, Spring WebClient for outbound HTTP, and Spring Mail/Jakarta Mail for SMTP. Deviations require architecture approval.
- **LLM Integration**: Use HTTPS JSON APIs with API-key auth stored in the secrets manager; prompts live in `src/main/resources/prompts/` and must be versioned.
- **Email Delivery**: Store templates in `src/main/resources/templates/` with paired HTML/text variants, and send via a provider that supports rich text (e.g., SES, Postmark). Capture provider message IDs for traceability.
- **Configuration & Secrets**: Manage via Spring Config + environment variables; never persist raw LLM responses beyond transient processing logs.
- **Observability**: Emit Micrometer metrics (`llm.latency`, `email.sent`, `email.fallback_triggered`) and structured logs with trace IDs propagated through LLM and SMTP calls. Alerts fire when fallback rates exceed 1% per hour.

## Workflow & Quality Gates

- **Definition of Ready**: A feature cannot enter implementation until the OpenAPI delta, prompt contract, and email template outline are documented, along with acceptance tests for success/failure paths.
- **Testing Expectations**: Unit tests back every controller/service; contract tests stub the LLM client; snapshot/integration tests render HTML + plaintext outputs; at least one automated scenario exercises the full request → LLM → email pipeline using recorded fixtures.
- **Code Review Checklist**: PRs must cite which principle they satisfy, attach contract diffs, and show evidence that fallbacks, validation, and accessibility checks are covered. No merge if any checklist item is unresolved.
- **Release Gate**: A release candidate must demonstrate zero critical alerts in staging for 24 hours and produce a sample outbound email approved by product/UX.

## Governance

This constitution supersedes other development practices for the Smart Letter service. Amendments require an RFC describing the motivation, risk assessment, and migration plan, plus approval from the service tech lead and product owner. Version changes follow semantic rules: MAJOR for removals or redefinitions of principles/sections, MINOR for new principles or expanded guardrails, PATCH for clarifications that do not change obligations. Every merged feature plan/spec/tasks document must include a "Constitution Check" section that records compliance evidence. The release engineer schedules quarterly compliance reviews; any violations must be remediated before the next release or explicitly waived with documented risk acceptance.

**Version**: 1.0.0 | **Ratified**: 2025-11-22 | **Last Amended**: 2025-11-22
