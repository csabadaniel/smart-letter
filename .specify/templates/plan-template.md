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
**Testing**: JUnit 5, Spring Boot Test, Testcontainers, AssertJ  
**Target Platform**: Linux container (x86_64) behind the shared HTTPS gateway  
**Project Type**: Backend microservice  
**Performance Goals**: LLM round-trip < 3s p95, email dispatch < 5s p95 unless tighter SLAs are required  
**Constraints**: Contract-first OpenAPI, HTML + plaintext email pairing, deterministic fallback path, Micrometer metrics for llm/email events  
**Scale/Scope**: Baseline 10k rich-text emails per day; override with feature-specific demand if known

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Contract readiness**: OpenAPI delta, DTO validation rules, and backward compatibility strategy are documented and approved (Principle I).
- **LLM safety**: Prompt contract, redaction controls, timeout budget, and fallback narrative are captured (Principle II).
- **Email integrity**: HTML + plaintext templates, accessibility requirements, and snapshot testing approach are agreed (Principle III).
- **Observability/Fallback metrics**: Micrometer metrics, alert thresholds, and release verification plan are listed under Risks/Mitigations.

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
```

**Structure Decision**: This layout is the default; document any additions (e.g., extra modules) and how they respect the constitution guardrails.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

> Use this table to record any deviation from contract-first APIs, the LLM safety envelope, or rich-email requirements.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., Skipping fallback email] | [current need] | [why deterministic fallback insufficient] |
| [e.g., Second outbound channel] | [specific problem] | [why email-only scope insufficient] |
