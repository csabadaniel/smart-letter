# Quickstart - Recipient & Prompt Configuration API

This guide shows how to exercise the configuration endpoints locally while honoring the Smart Letter constitution guardrails.

## Prerequisites
- Java 21 and Maven Wrapper (`./mvnw`) available
- Docker installed (required for Testcontainers + Firestore emulator)
- Valid `X-SmartLetter-Api-Key` for test environment stored in `.env.local` (never commit)
- Google Cloud CLI installed (`brew install --cask google-cloud-sdk`) with Application Default Credentials configured via `gcloud auth application-default login`; required because Spring Cloud GCP Firestore auto-config loads ADC even when using the emulator.

## 0. Rehydrate the Spring Initializr Scaffold (only if files are missing)
Run the constitution-mandated command whenever you need to recreate the baseline project structure:
```bash
curl https://start.spring.io/starter.zip \
  -d dependencies=web,validation,data-firestore,actuator \
  -d javaVersion=21 \
  -d language=java \
  -d type=maven-project \
  -d packageName=com.smartletter \
  -o smart-letter.zip
unzip -o smart-letter.zip -d /tmp/smart-letter-init
rsync -a /tmp/smart-letter-init/ /Users/csaba.daniel/vscode-projects/smart-letter/
rm -rf smart-letter.zip /tmp/smart-letter-init
```

## 1. Configure Firestore Emulator Environment
Export the emulator host/port and project IDs before running the app outside Testcontainers:
```bash
export FIRESTORE_EMULATOR_HOST=localhost:8686
export SPRING_CLOUD_GCP_PROJECT_ID=smart-letter-local
export SPRING_CLOUD_GCP_FIRESTORE_PROJECT_ID=$SPRING_CLOUD_GCP_PROJECT_ID
export SPRING_CLOUD_GCP_FIRESTORE_EMULATOR_HOST=$FIRESTORE_EMULATOR_HOST
```
Testcontainers starts the emulator automatically during `./mvnw clean verify -Pfirestore-emulator`, but the above exports are required for `spring-boot:run` and manual Swagger tests.

## 2. Load the Local API Key
Store your test key in `.env.local` (gitignored) as `SMARTLETTER_API_KEY=...`, then load it without echoing to history:
```bash
set -a
source .env.local
set +a
```
Never commit `.env.local`; rotate keys via Secret Manager when collaborating.

## 3. Install Dependencies
```bash
cd /Users/csaba.daniel/vscode-projects/smart-letter
./mvnw --version
```
The Maven Wrapper downloads Maven 3.9.x automatically; no extra setup is required.

## 4. Run Unit + Integration + BDD Suites
```bash
./mvnw clean verify -Pcucumber -Pfirestore-emulator
```
- Launches the Firestore emulator via Testcontainers
- Executes JUnit/AssertJ suites, controller slices, Firestore repository ITs, and `config_management.feature`
- Fails fast if ASCII-only doc lint, coverage (<95% for changed files), or OpenAPI drift is detected

## 5. Generate OpenAPI Contract
```bash
./mvnw springdoc-openapi:generate
```
- Regenerates `docs/contracts/openapi.yaml`
- Compare against `specs/001-email-config-endpoint/contracts/openapi.yaml` preview to confirm shape
- Commit the regenerated artifact when controller annotations change

## 6. Start the Application Locally
```bash
./mvnw spring-boot:run \
  -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.arguments="--smartletter.api-key.test=$SMARTLETTER_API_KEY --spring.cloud.gcp.firestore.project-id=smart-letter-local"
```
- Uses the Firestore emulator when `FIRESTORE_EMULATOR_HOST` is set (export before running if needed)
- Service listens on `http://localhost:8080`

## 7. Exercise Endpoints via Swagger UI
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

## 8. Observability Smoke Test
- After a successful PUT, check logs for `ConfigurationAuditEvent` entries (actor hash, version) and ensure raw prompt text never appears
- Use `/actuator/prometheus` or Micrometer registry logs to confirm `config.update.success` increments
- Trigger a validation error to see `config.update.failure{reason="validation"}` increment; ensure alert thresholds remain below chaos-test limits

## 9. Terraform & Cloud Run Touchpoints
- Update required variables in `infra/cloudrun/main.tf` and `infra/firestore/app_settings.tf`
- Run:
  ```bash
  cd infra
  terraform init
  terraform plan -out=config-plan.tfplan
  ```
- Attach `config-plan.tfplan` (or its textual summary) to your PR as required evidence

## 10. ASCII Documentation Check
Before committing docs, run the ASCII lint to avoid constitution violations:
```bash
./scripts/ascii-scan.sh specs/001-email-config-endpoint
```

Following these steps keeps local development aligned with CI/CD pipelines and proves the feature satisfies audit, observability, and documentation guardrails before implementation begins.
