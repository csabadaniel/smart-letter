# Smart Letter Infrastructure

This directory now tracks Terraform modules for Firestore seeding and Cloud Run delivery configuration settings.

## Firestore (Delivery Configuration Seed)
1. `cd infra/firestore`
2. Export `GOOGLE_APPLICATION_CREDENTIALS` or authenticate with `gcloud auth application-default login`.
3. Run `terraform init` (first time only).
4. Plan and capture an artifact for the PR: `terraform plan -out=../../artifacts/firestore.plan`.
5. Apply in the appropriate environment once the PR is approved: `terraform apply ../../artifacts/firestore.plan`.

The `app_settings.tf` module creates the `appSettings/configuration/delivery/main` document with sanitized defaults that match the application cache expectations.

## Cloud Run (Runtime Delivery Config)
1. `cd infra/cloudrun`
2. Configure variables via `terraform.tfvars` or `-var` flags for `project_id`, `service_account_email`, `api_key_secret_name`, etc.
3. Run `terraform init`.
4. Produce the plan artifact referenced in PR checklists: `terraform plan -out=../../artifacts/cloudrun.plan`.
5. Apply after review: `terraform apply ../../artifacts/cloudrun.plan`.

`main.tf` ensures Cloud Run receives:
- `DELIVERY_CONFIG_COLLECTION_PATH`
- `DELIVERY_CONFIG_CACHE_TTL_SECONDS`
- `SMARTLETTER_API_KEY_ENVIRONMENT`
- Secret-based API keys via `env_from.secret_ref`

These variables keep runtime configuration aligned with the DeliveryConfigurationCache implementation.
