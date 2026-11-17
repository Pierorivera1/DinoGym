# The Go API on Lambda (decision 63): arm64, OS-only runtime, binary named bootstrap.

resource "aws_iam_role" "api" {
  name = "${local.name}-api"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "lambda.amazonaws.com" }
      Action    = "sts:AssumeRole"
    }]
  })
}

# Only what the code does: write its own logs, use its own table, read its own secrets.
resource "aws_iam_role_policy" "api" {
  name = "api"
  role = aws_iam_role.api.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = "${aws_cloudwatch_log_group.api.arn}:*"
      },
      {
        Effect = "Allow"
        Action = [
          "dynamodb:GetItem",
          "dynamodb:PutItem",
          "dynamodb:UpdateItem",
          "dynamodb:Query",
        ]
        Resource = [aws_dynamodb_table.main.arn, "${aws_dynamodb_table.main.arn}/index/*"]
      },
      {
        Effect   = "Allow"
        Action   = "ssm:GetParameters"
        Resource = [for p in aws_ssm_parameter.secret : p.arn]
      },
    ]
  })
}

# Decision 22: Terraform owns the log group so retention is 7 days, not forever.
resource "aws_cloudwatch_log_group" "api" {
  name              = "/aws/lambda/${local.name}-api"
  retention_in_days = 7
}

resource "aws_lambda_function" "api" {
  function_name    = "${local.name}-api"
  role             = aws_iam_role.api.arn
  runtime          = "provided.al2023"
  architectures    = ["arm64"]
  handler          = "bootstrap"
  filename         = var.lambda_zip
  source_code_hash = filebase64sha256(var.lambda_zip)
  memory_size      = 256
  timeout          = 10

  logging_config {
    log_format = "JSON"
    log_group  = aws_cloudwatch_log_group.api.name
  }

  # Read by backend/internal/config. The log level follows DINOGYM_ENV (decision 22).
  environment {
    variables = {
      DINOGYM_ENV       = var.env
      DINOGYM_TABLE     = aws_dynamodb_table.main.name
      DINOGYM_TELEMETRY = "none" # Grafana Cloud is wired up in task 26 (decision 64)
    }
  }

  depends_on = [aws_iam_role_policy.api]
}
