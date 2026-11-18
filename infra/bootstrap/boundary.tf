# Permissions boundaries: the ceiling for every role a deploy role creates.
#
# A deploy role must create the Lambda's role and write its policy, so on its
# own it could write an admin policy onto a role it made, then use that role.
# A boundary closes that gap: a role's effective permissions are its policies
# AND its boundary, so a role carrying this one can never do more than the
# environment's roles need (the API, the reminder function and the role
# Scheduler uses to invoke it), whatever policy is written onto it. The deploy roles may create or
# change roles only when this boundary is attached (github.tf).
#
# Keep it in step with the role policies in infra/modules/dinogym/lambda.tf and
# reminders.tf: anything a role needs must be allowed in both.

resource "aws_iam_policy" "boundary" {
  for_each = local.envs

  name        = "dinogym-${each.key}-boundary"
  description = "Ceiling for roles created by dinogym-deploy-${each.key}: what the ${each.key} API may ever do."
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "OwnLogs"
        Effect   = "Allow"
        Action   = ["logs:CreateLogStream", "logs:PutLogEvents"]
        Resource = "arn:aws:logs:us-east-1:${local.account_id}:log-group:/aws/lambda/dinogym-${each.key}-*:*"
      },
      {
        Sid    = "OwnTable"
        Effect = "Allow"
        Action = [
          "dynamodb:GetItem",
          "dynamodb:PutItem",
          "dynamodb:UpdateItem",
          "dynamodb:DeleteItem",
          "dynamodb:Query",
        ]
        Resource = [
          "arn:aws:dynamodb:us-east-1:${local.account_id}:table/dinogym-${each.key}",
          "arn:aws:dynamodb:us-east-1:${local.account_id}:table/dinogym-${each.key}/index/*",
        ]
      },
      {
        Sid    = "OwnSecrets"
        Effect = "Allow"
        Action = "ssm:GetParameters"
        Resource = [
          "arn:aws:ssm:us-east-1:${local.account_id}:parameter/dinogym-${each.key}/session-signing-key",
          "arn:aws:ssm:us-east-1:${local.account_id}:parameter/dinogym-${each.key}/grafana-token",
        ]
      },
      {
        # Decrypting those SecureStrings with the AWS-managed aws/ssm key, and
        # only through SSM (decision 79).
        Sid       = "DecryptOwnSecretsViaSSM"
        Effect    = "Allow"
        Action    = "kms:Decrypt"
        Resource  = "arn:aws:kms:us-east-1:${local.account_id}:key/*"
        Condition = { StringEquals = { "kms:ViaService" = "ssm.us-east-1.amazonaws.com" } }
      },
      {
        # The API keeps each user's reminder schedules (decision 105).
        Sid    = "OwnSchedules"
        Effect = "Allow"
        Action = [
          "scheduler:CreateSchedule",
          "scheduler:UpdateSchedule",
          "scheduler:DeleteSchedule",
          "scheduler:GetSchedule",
        ]
        Resource = "arn:aws:scheduler:us-east-1:${local.account_id}:schedule/dinogym-${each.key}/*"
      },
      {
        # ...and hands those schedules the role that invokes the reminder function.
        Sid       = "PassSchedulerRole"
        Effect    = "Allow"
        Action    = "iam:PassRole"
        Resource  = "arn:aws:iam::${local.account_id}:role/dinogym-${each.key}-scheduler"
        Condition = { StringEquals = { "iam:PassedToService" = "scheduler.amazonaws.com" } }
      },
      {
        # The Scheduler role's one job.
        Sid      = "InvokeReminders"
        Effect   = "Allow"
        Action   = "lambda:InvokeFunction"
        Resource = "arn:aws:lambda:us-east-1:${local.account_id}:function:dinogym-${each.key}-reminders"
      },
    ]
  })
}
