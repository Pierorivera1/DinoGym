# DinoGym backend for one environment: the DynamoDB table, the Go Lambda behind
# an API Gateway HTTP API, and the SSM parameters for its secrets.

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

# Decision 54: Terraform creates each secret with a placeholder and never
# touches the value again. The owner sets it once with the AWS CLI, so it stays
# out of state and git.
resource "aws_ssm_parameter" "secret" {
  for_each = toset(["session-signing-key", "grafana-token"])

  name  = "/${local.name}/${each.key}"
  type  = "SecureString"
  value = "unset"

  lifecycle {
    ignore_changes = [value]
  }
}
