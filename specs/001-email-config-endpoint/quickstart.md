# Quickstart - Recipient & Prompt Configuration API

This guide shows how to exercise the configuration endpoints locally while honoring the Smart Letter constitution guardrails.

## Prerequisites
- Java 21 and Maven Wrapper (`./mvnw`) available
- Docker installed (required for Testcontainers + Firestore emulator)
- Valid `X-SmartLetter-Api-Key` for test environment stored in `.env.local` (never commit)
- Google Cloud CLI installed (`brew install --cask google-cloud-sdk`) with Application Default Credentials configured via `gcloud auth application-default login`; required because Spring Cloud GCP Firestore auto-config loads ADC even when using the emulator.

## 1. Install Dependencies
```bash
cd /Users/csaba.daniel/vscode-projects/smart-letter
./mvnw --version
```
The Maven Wrapper downloads Maven 3.9.x automatically; no extra setup is required.

## 2. Run Unit + Integration + BDD Suites
```bash
./mvnw clean verify -Pcucumber -Pfirestore-emulator
```
- Launches the Firestore emulator via Testcontainers
- Executes JUnit/AssertJ suites, controller slices, Firestore repository ITs, and `config_management.feature`
- Fails fast if ASCII-only doc lint, coverage (<95% for changed files), or OpenAPI drift is detected

## 3. Generate OpenAPI Contract
```bash
./mvnw springdoc-openapi:generate
```
- Regenerates `docs/contracts/openapi.yaml`
- Compare against `specs/001-email-config-endpoint/contracts/openapi.yaml` preview to confirm shape
- Commit the regenerated artifact when controller annotations change

## 4. Start the Application Locally
```bash
./mvnw spring-boot:run \
  -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments="--smartletter.api-key.test=$SMARTLETTER_API_KEY --spring.cloud.gcp.firestore.project-id=smart-letter-local"
```
- Uses the Firestore emulator when `FIRESTORE_EMULATOR_HOST` is set (export before running if needed)
- Service listens on `http://localhost:8080`

## 5. Exercise Endpoints via Swagger UI
1. Open `http://localhost:8080/swagger-ui/index.html`
2. Enter your API key when prompted; Swagger stores it in session storage only
3. Invoke `PUT /v1/config/delivery` with JSON payload:
   ```json
   {
     "recipientEmail": "ops@example.com",
     "llmPrompt": "You are Smart Letter..."
   }
   ```
4. Verify HTTP 200 response includes `version`, `updatedAt`, `updatedBy`
5. Call `GET /v1/config/delivery`, confirm headers `ETag` and `Last-Modified`

## 6. Observability Smoke Test
- After a successful PUT, check logs for `ConfigurationAuditEvent` entries (actor hash, version) and ensure raw prompt text never appears
- Use `/actuator/prometheus` or Micrometer registry logs to confirm `config.update.success` increments
- Trigger a validation error to see `config.update.failure{reason="validation"}` increment; ensure alert thresholds remain below chaos-test limits

## 7. Terraform & Cloud Run Touchpoints
- Update required variables in `infra/cloudrun/main.tf` and `infra/firestore/app_settings.tf`
- Run:
  ```bash
  cd infra
  terraform init
  terraform plan -out=config-plan.tfplan
  ```
- Attach `config-plan.tfplan` (or its textual summary) to your PR as required evidence

## 8. ASCII Documentation Check
Before committing docs, run the ASCII lint to avoid constitution violations:
```bash
./scripts/ascii-scan.sh specs/001-email-config-endpoint
```

Following these steps keeps local development aligned with CI/CD pipelines and proves the feature satisfies audit, observability, and documentation guardrails before implementation begins.
