# Implementation Plan: [FEATURE]

**Branch**: `[###-feature-name]` | **Date**: [DATE] | **Spec**: [link]
**Input**: Feature specification from `/specs/[###-feature-name]/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command and inherits the Smart Letter constitution guardrails.

## Summary

[Extract from feature spec: primary requirement + technical approach from research]

## Technical Context

**Language/Version**: Java 21 (Spring Boot 3.3.x)  
**Primary Dependencies**: Spring Web, Spring WebClient, Spring Validation, Thymeleaf, Spring Mail, Micrometer (note deviations explicitly)  
**Storage**: PostgreSQL email audit log (state `N/A` only if the feature is entirely stateless)  
**Testing**: JUnit 5, Spring Boot Test, AssertJ, Mockito, Spring Cloud Contract, Testcontainers, Cucumber JVM/JGiven  
**Target Platform**: Linux container (x86_64) behind the shared HTTPS gateway  
**Project Type**: Backend microservice  
**Performance Goals**: LLM round-trip < 3s p95, email dispatch < 5s p95 unless tighter SLAs are required  
**Constraints**: Contract-first OpenAPI, HTML + plaintext email pairing, deterministic fallback path, Micrometer metrics for llm/email events, authenticated Swagger UI exposure, API key enforcement via `X-SmartLetter-Api-Key`, and INVEST-compliant story slicing  
**Scale/Scope**: Baseline 10k rich-text emails per day; override with feature-specific demand if known  
**Containerization & Deployment**: Build OCI images via Paketo Buildpacks or Jib, publish to Artifact Registry, and target Cloud Run (Always Free tier: ≤1 vCPU, ≤256 MiB, ≤20 concurrency) with `gcloud run deploy` automation recorded here

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Contract readiness**: OpenAPI delta, DTO validation rules, and backward compatibility strategy are documented and approved (Principle I).
- **LLM safety**: Prompt contract, redaction controls, timeout budget, and fallback narrative are captured (Principle II).
- **Email integrity**: HTML + plaintext templates, accessibility requirements, and snapshot testing approach are agreed (Principle III).
- **Observability/Fallback metrics**: Micrometer metrics, alert thresholds, and release verification plan are listed under Risks/Mitigations.
- **Container/GCP budget**: Docker/Buildpacks approach, Artifact Registry repo, Cloud Run settings (region, memory, concurrency), and Always Free compliance plan are documented (Service Guardrails).
- **Swagger UI exposure**: Swagger/OpenAPI bundle location, route (`/swagger-ui`), auth method (IAP/basic), and manual test plan are documented; Try-It-Out policy is stated (Service Guardrails).
- **API key policy**: Header name, rotation cadence, storage (Secret Manager), and rate limiting/audit plan are specified, plus how Swagger UI accepts user-provided keys (Service Guardrails).
- **INVEST cadence**: Feature stories are decomposed into INVEST slices with clearly stated acceptance criteria, estimated effort, and iteration order; WIP limits and incremental delivery plan are captured (Workflow & Quality Gates).
- **TDD/BDD plan**: Identify which stories will add JUnit/AssertJ unit tests, Spring Cloud Contract stubs, Testcontainers flows, and Gherkin scenarios (Cucumber). Note how “red → green → refactor” evidence will be captured in commits/PRs (Workflow & Quality Gates).

### Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # /speckit.plan output
├── research.md          # Phase 0 (/speckit.plan)
├── data-model.md        # Phase 1 (/speckit.plan)
├── quickstart.md        # Phase 1 (/speckit.plan)
├── contracts/openapi.yaml   # Contract-first source of truth
└── tasks.md             # Phase 2 (/speckit.tasks)
```

### Source Code (repository root)

```text
docs/
└── contracts/openapi.yaml

src/main/java/com/smartletter/
├── api/            # Controllers + DTOs
├── llm/            # Client, prompt builders, safety filters
├── email/          # Template renderers + deliverability utilities
├── service/        # Domain orchestration
└── config/         # Spring configuration, metrics, exception mapping

src/main/resources/
├── templates/      # HTML + plaintext email pairs
├── prompts/        # Versioned system/user prompt assets
└── application.yml

src/test/java/com/smartletter/
├── contract/       # LLM + SMTP contract tests
├── integration/    # request → LLM → email flow
└── unit/

src/test/resources/
└── templates/__snapshots__/   # Email snapshot baselines

infra/
├── cloudrun/service.yaml      # Cloud Run defaults (region, CPU/memory, concurrency)
├── scripts/deploy-cloudrun.sh # `gcloud run deploy` helper with Always Free flags
└── Dockerfile (optional)      # Only if deviating from Buildpacks/Jib defaults
```

**Structure Decision**: This layout is the default; document any additions (e.g., extra modules) and how they respect the constitution guardrails.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

> Use this table to record any deviation from contract-first APIs, the LLM safety envelope, rich-email requirements, or containerization/GCP deployment guardrails.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., Skipping fallback email] | [current need] | [why deterministic fallback insufficient] |
| [e.g., Second outbound channel] | [specific problem] | [why email-only scope insufficient] |
