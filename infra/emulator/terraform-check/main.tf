# Probe for task 14: does our kind of Terraform apply cleanly to the AWS
# Emulator? Not the real infrastructure (that is task 17). Run it only in a
# shell where infra/emulator/env.sh is sourced; the AWS provider then reads
# the endpoint, credentials and certificate from the environment.

terraform {
  required_version = ">= 1.17"
  required_providers {
    aws = {
      source = "hashicorp/aws"
      # 6.23.0 and later read S3 bucket tags through S3 Control, which the
      # emulator cannot serve (aws/aws-emulator#56). Decision 58.
      version = "= 6.22.0"
    }
  }
}

provider "aws" {
  region = "us-east-1"
  # The emulator is reached by IP, so S3 must put the bucket in the path.
  s3_use_path_style = true
}

# Decision 52: one table, keyed by user, GSI1 on the change number.
resource "aws_dynamodb_table" "main" {
  name         = "dinogym-tfcheck"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "PK"
  range_key    = "SK"

  attribute {
    name = "PK"
    type = "S"
  }
  attribute {
    name = "SK"
    type = "S"
  }
  attribute {
    name = "GSI1PK"
    type = "S"
  }
  attribute {
    name = "GSI1SK"
    type = "N"
  }

  global_secondary_index {
    name            = "GSI1"
    hash_key        = "GSI1PK"
    range_key       = "GSI1SK"
    projection_type = "ALL"
  }
}

resource "aws_sqs_queue" "jobs" {
  name = "dinogym-tfcheck-jobs"
}

resource "aws_s3_bucket" "files" {
  bucket = "dinogym-tfcheck-files"
}

# Reminders (task 24): Scheduler → SQS, with a role allowed to send.
resource "aws_iam_role" "scheduler" {
  name = "dinogym-tfcheck-scheduler"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "scheduler.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

resource "aws_iam_role_policy" "scheduler_send" {
  name = "send"
  role = aws_iam_role.scheduler.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "sqs:SendMessage"
      Resource = aws_sqs_queue.jobs.arn
    }]
  })
}

resource "aws_scheduler_schedule" "weekly" {
  name                         = "dinogym-tfcheck-weekly"
  schedule_expression          = "cron(0 7 ? * MON *)"
  schedule_expression_timezone = "America/Lima"
  flexible_time_window {
    mode = "OFF"
  }
  target {
    arn      = aws_sqs_queue.jobs.arn
    role_arn = aws_iam_role.scheduler.arn
    input    = jsonencode({ type = "weekly-summary" })
  }
  depends_on = [aws_iam_role_policy.scheduler_send]
}
