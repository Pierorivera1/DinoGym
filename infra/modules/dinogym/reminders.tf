# Reminders (task 24, decisions 105–107): a schedule group the API fills with
# one schedule per user and kind, the role those schedules invoke with, and
# the reminder Lambda that sends FCM data messages.

resource "aws_scheduler_schedule_group" "reminders" {
  name = local.name
}

# Scheduler assumes this role to invoke the reminder function, and nothing else.
resource "aws_iam_role" "scheduler" {
  name                 = "${local.name}-scheduler"
  permissions_boundary = var.permissions_boundary
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "scheduler.amazonaws.com" }
      Action    = "sts:AssumeRole"
      # Only schedules in this environment's group, in this account.
      Condition = {
        StringEquals = { "aws:SourceAccount" = data.aws_caller_identity.current.account_id }
        ArnLike      = { "aws:SourceArn" = "arn:aws:scheduler:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:schedule/${local.name}/*" }
      }
    }]
  })
}

resource "aws_iam_role_policy" "scheduler" {
  name = "invoke-reminders"
  role = aws_iam_role.scheduler.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "lambda:InvokeFunction"
      Resource = aws_lambda_function.reminders.arn
    }]
  })
}

resource "aws_iam_role" "reminders" {
  name                 = "${local.name}-reminders"
  permissions_boundary = var.permissions_boundary
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

# Read settings and devices, delete devices FCM no longer knows, read the
# Grafana token (decision 118). No other secret, no writes to synced rows. Reaching Google needs no AWS permission: the
# federation signs a GetCallerIdentity request, which every role may make.
resource "aws_iam_role_policy" "reminders" {
  name = "reminders"
  role = aws_iam_role.reminders.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = "${aws_cloudwatch_log_group.reminders.arn}:*"
      },
      {
        Effect   = "Allow"
        Action   = ["dynamodb:Query", "dynamodb:DeleteItem"]
        Resource = aws_dynamodb_table.main.arn
      },
      {
        Effect   = "Allow"
        Action   = "ssm:GetParameters"
        Resource = local.grafana_arn
      },
      # As for the API: decrypt with the aws/ssm key, only through SSM.
      {
        Effect    = "Allow"
        Action    = "kms:Decrypt"
        Resource  = "arn:aws:kms:${data.aws_region.current.region}:${data.aws_caller_identity.current.account_id}:key/*"
        Condition = { StringEquals = { "kms:ViaService" = "ssm.${data.aws_region.current.region}.amazonaws.com" } }
      },
    ]
  })
}

resource "aws_cloudwatch_log_group" "reminders" {
  name              = "/aws/lambda/${local.name}-reminders"
  retention_in_days = 7
}

resource "aws_lambda_function" "reminders" {
  function_name    = "${local.name}-reminders"
  role             = aws_iam_role.reminders.arn
  runtime          = "provided.al2023"
  architectures    = ["arm64"]
  handler          = "bootstrap"
  filename         = var.reminders_zip
  source_code_hash = filebase64sha256(var.reminders_zip)
  memory_size      = 128
  timeout          = 30

  logging_config {
    log_format = "JSON"
    log_group  = aws_cloudwatch_log_group.reminders.name
  }

  # Read by backend/cmd/reminders. None of these is secret (decision 107).
  environment {
    variables = merge(local.telemetry_env, {
      DINOGYM_ENV                 = var.env
      DINOGYM_TABLE               = aws_dynamodb_table.main.name
      DINOGYM_FCM_PROJECT         = var.fcm.project
      DINOGYM_GCP_AUDIENCE        = var.fcm.audience
      DINOGYM_FCM_SERVICE_ACCOUNT = var.fcm.service_account
    })
  }

  depends_on = [aws_iam_role_policy.reminders]
}
