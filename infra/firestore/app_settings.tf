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
}

variable "project_id" {
  description = "Google Cloud project that hosts the Firestore database"
  type        = string
}

variable "delivery_config_collection_path" {
  description = "Fully qualified Firestore path for the delivery configuration document"
  type        = string
  default     = "appSettings/configuration/delivery"
}

variable "delivery_config_seed" {
  description = "Initial document seed used when the Firestore collection is empty"
  type = object({
    recipient_email = string
    llm_prompt      = string
    updated_by      = string
    version         = number
  })
  default = {
    recipient_email = "ops@example.com"
    llm_prompt      = "Describe Smart Letter delivery configuration requirements."
    updated_by      = "terraform:seed"
    version         = 1
  }
}

locals {
  # Firestore paths alternate collection/document segments. The delivery document lives under
  # appSettings (collection) / configuration (document) / delivery (collection) / main (document).
  path_segments = split("/", var.delivery_config_collection_path)

  configuration_doc = {
    collection  = element(local.path_segments, 0)
    document_id = element(local.path_segments, 1)
  }

  delivery_doc = {
    collection  = try(element(local.path_segments, 2), "delivery")
    document_id = try(element(local.path_segments, 3), "main")
  }

  delivery_fields = jsonencode({
    recipientEmail = { stringValue = var.delivery_config_seed.recipient_email }
    llmPrompt      = { stringValue = var.delivery_config_seed.llm_prompt }
    promptSha256   = { stringValue = sha256(var.delivery_config_seed.llm_prompt) }
    updatedBy      = { stringValue = var.delivery_config_seed.updated_by }
    version        = { integerValue = floor(var.delivery_config_seed.version) }
  })
}

resource "google_firestore_document" "configuration" {
  project     = var.project_id
  database    = "(default)"
  collection  = local.configuration_doc.collection
  document_id = local.configuration_doc.document_id
  fields      = jsonencode({ seedReason = { stringValue = "Smart Letter delivery configuration root" } })
}

resource "google_firestore_document" "delivery" {
  depends_on = [google_firestore_document.configuration]

  project     = var.project_id
  database    = "(default)"
  collection  = local.delivery_doc.collection
  document_id = local.delivery_doc.document_id
  parent_path = google_firestore_document.configuration.name
  fields      = local.delivery_fields
}

output "delivery_configuration_document" {
  description = "Fully qualified Firestore name for the seeded delivery configuration document"
  value       = google_firestore_document.delivery.name
}
