# DinoGym backend for one environment: the DynamoDB table, the Go Lambda behind
# an API Gateway HTTP API, and access to its secrets in SSM.

locals {
  name = "dinogym-${var.env}"
}

# Decisions 52 and 65: one on-demand table; GSI1 is keyed on PK and changeNumber.
# Must match store.CreateTable in backend/internal/store/store.go.
resource "aws_dynamodb_table" "main" {
  name                        = local.name
  billing_mode                = "PAY_PER_REQUEST"
  hash_key                    = "PK"
  range_key                   = "SK"
  deletion_protection_enabled = var.protect_data

  attribute {
    name = "PK"
    type = "S"
  }
  attribute {
    name = "SK"
    type = "S"
  }
  attribute {
    name = "changeNumber"
    type = "N"
  }

  global_secondary_index {
    name            = "GSI1"
    hash_key        = "PK"
    range_key       = "changeNumber"
    projection_type = "ALL"
  }

  point_in_time_recovery {
    enabled = var.protect_data
  }
}

# Decision 54: the secrets are SSM SecureString parameters that the owner
# creates with the AWS CLI (infra/README.md). Terraform does not manage them,
# because the AWS provider reads a parameter's decrypted value into state on
# every refresh. It only grants the Lambda access to them, by name.
data "aws_region" "current" {}
data "aws_caller_identity" "current" {}

locals {
  secret_arns = [
    for s in ["session-signing-key", "grafana-token"] :
    "arn:aws:ssm:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:parameter/${local.name}/${s}"
  ]
}
