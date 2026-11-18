# Applied by hand by the owner (task 24, decision 107), like infra/bootstrap.
# Lets the reminder Lambdas send FCM messages with no Google key (decision 55):
# a workload identity pool that trusts only the two reminder roles of our AWS
# account, and a service account that may only send FCM messages.
#
#   gcloud auth application-default login
#   cd infra/google
#   terraform init -backend-config="bucket=dinogym-tfstate-<account-id>"   # needs AWS credentials, infra/README.md
#   terraform apply -var aws_account_id=<account-id>
#
# The outputs go into envs/staging and envs/prod (module input `fcm`).

terraform {
  required_version = "= 1.17.2" # decision 18
  required_providers {
    google = {
      source  = "hashicorp/google"
      version = "= 8.10.0"
    }
  }

  # State in the bootstrap's bucket, next to the AWS roots (decision 66).
  backend "s3" {
    key          = "google/terraform.tfstate"
    region       = "us-east-1"
    use_lockfile = true
    encrypt      = true
  }
}

variable "project" {
  description = "The Firebase project (decision 55)."
  type        = string
  default     = "dinogym-3870b"
}

variable "aws_account_id" {
  description = "The AWS account whose reminder roles may act as the FCM sender. An id, not a secret."
  type        = string
  validation {
    condition     = can(regex("^[0-9]{12}$", var.aws_account_id))
    error_message = "aws_account_id is 12 digits."
  }
}

provider "google" {
  project = var.project
}

data "google_project" "this" {}

locals {
  # The only AWS roles the pool accepts: the reminder function's, per environment.
  reminder_roles = {
    for env in ["staging", "prod"] :
    env => "arn:aws:sts::${var.aws_account_id}:assumed-role/dinogym-${env}-reminders"
  }
}

resource "google_project_service" "apis" {
  for_each = toset([
    "iam.googleapis.com",            # service accounts, workload identity pools
    "iamcredentials.googleapis.com", # generateAccessToken (impersonation)
    "sts.googleapis.com",            # the token exchange
    "fcm.googleapis.com",            # FCM HTTP v1
  ])
  service = each.value
  # Turning an API off on destroy could break Firebase for the app.
  disable_on_destroy = false
}

resource "google_iam_workload_identity_pool" "aws" {
  workload_identity_pool_id = "dinogym-aws"
  display_name              = "DinoGym AWS"
  description               = "The DinoGym reminder Lambdas (decision 107)."
  depends_on                = [google_project_service.apis]
}

resource "google_iam_workload_identity_pool_provider" "aws" {
  workload_identity_pool_id          = google_iam_workload_identity_pool.aws.workload_identity_pool_id
  workload_identity_pool_provider_id = "aws-account"
  display_name                       = "AWS account"

  aws {
    account_id = var.aws_account_id
  }

  # Google's documented mapping for AWS: aws_role is the assumed role's ARN
  # without the session name.
  attribute_mapping = {
    "google.subject"     = "assertion.arn"
    "attribute.aws_role" = "assertion.arn.contains('assumed-role') ? assertion.arn.extract('{account_arn}assumed-role/') + 'assumed-role/' + assertion.arn.extract('assumed-role/{role_name}/') : assertion.arn"
  }
  # Any other identity in the account is refused at the exchange, before any
  # service account binding is looked at.
  attribute_condition = join(" || ", [for arn in values(local.reminder_roles) : "assertion.arn.startsWith('${arn}/')"])
}

resource "google_service_account" "fcm_sender" {
  account_id   = "fcm-sender"
  display_name = "DinoGym FCM sender"
  description  = "Sends reminders. Reached only through workload identity federation; it has no keys."
  depends_on   = [google_project_service.apis]
}

# Only what sending needs. firebasecloudmessaging.admin is the narrowest role
# that holds cloudmessaging.messages.create.
resource "google_project_iam_member" "fcm_send" {
  project = var.project
  role    = "roles/firebasecloudmessaging.admin"
  member  = "serviceAccount:${google_service_account.fcm_sender.email}"
}

resource "google_service_account_iam_member" "reminder_roles" {
  for_each           = local.reminder_roles
  service_account_id = google_service_account.fcm_sender.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "principalSet://iam.googleapis.com/${google_iam_workload_identity_pool.aws.name}/attribute.aws_role/${each.value}"
}

output "fcm" {
  description = "The module input `fcm` for envs/staging and envs/prod. Not secret."
  value = {
    project         = var.project
    audience        = "//iam.googleapis.com/${google_iam_workload_identity_pool_provider.aws.name}"
    service_account = google_service_account.fcm_sender.email
  }
}
