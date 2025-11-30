terraform {
  required_version = ">= 1.6.0"

  required_providers {
    google = {
      source  = "hashicorp/google"
      version = ">= 5.0"
    }
  }
}

provider "google" {
  project = var.project_id
  region  = var.region
}

variable "project_id" {
  description = "Google Cloud project hosting Smart Letter"
  type        = string
}

variable "region" {
  description = "Cloud Run region"
  type        = string
  default     = "us-central1"
}

variable "service_name" {
  description = "Cloud Run service name"
  type        = string
  default     = "smart-letter-api"
}

variable "service_account_email" {
  description = "Service account used by Cloud Run"
  type        = string
}

variable "container_image" {
  description = "OCI image for Smart Letter"
  type        = string
  default     = "us-docker.pkg.dev/example-project/smart-letter/app:latest"
}

variable "delivery_config_collection_path" {
  description = "Firestore path for the delivery configuration document"
  type        = string
  default     = "appSettings/configuration/delivery"
}

variable "delivery_config_cache_ttl_seconds" {
  description = "Cache TTL that API exposes via DeliveryConfigurationCache"
  type        = number
  default     = 60
}

variable "api_key_secret_name" {
  description = "Secret Manager secret storing the staging/prod API key"
  type        = string
}

variable "api_key_secret_key" {
  description = "Secret Manager version key to read"
  type        = string
  default     = "latest"
}

variable "container_concurrency" {
  description = "Cloud Run container concurrency"
  type        = number
  default     = 20
}

resource "google_cloud_run_service" "config_api" {
  name     = var.service_name
  location = var.region

  metadata {
    annotations = {
      "run.googleapis.com/ingress" = "internal-and-cloud-load-balancing"
    }
  }

  template {
    metadata {
      annotations = {
        "autoscaling.knative.dev/maxScale" = "5"
      }
    }

    spec {
      service_account_name = var.service_account_email
      container_concurrency = var.container_concurrency

      containers {
        image = var.container_image

        env {
          name  = "DELIVERY_CONFIG_COLLECTION_PATH"
          value = var.delivery_config_collection_path
        }

        env {
          name  = "DELIVERY_CONFIG_CACHE_TTL_SECONDS"
          value = tostring(var.delivery_config_cache_ttl_seconds)
        }

        env {
          name = "SMARTLETTER_API_KEY_ENVIRONMENT"
          value = "${terraform.workspace}" # test/prod derived from workspace name
        }

        env_from {
          secret_ref {
            name = var.api_key_secret_name
          }
        }

        env {
          name = "SMARTLETTER_API_KEY_SECRET_VERSION"
          value = var.api_key_secret_key
        }
      }
    }
  }

  traffic {
    percent         = 100
    latest_revision = true
  }

  lifecycle {
    ignore_changes = [
      metadata[0].annotations,
      template[0].metadata[0].annotations
    ]
  }
}

output "delivery_config_env" {
  description = "Environment variables wired into Cloud Run for delivery configuration"
  value = {
    collection_path = var.delivery_config_collection_path
    cache_ttl       = var.delivery_config_cache_ttl_seconds
  }
}
