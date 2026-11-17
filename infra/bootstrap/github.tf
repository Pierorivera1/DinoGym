# GitHub Actions signs in to AWS with OIDC: each job gets a short-lived token
# from GitHub, and AWS swaps it for credentials that last an hour. No AWS key
# is stored anywhere (CLAUDE.md).

resource "aws_iam_openid_connect_provider" "github" {
  url            = "https://token.actions.githubusercontent.com"
  client_id_list = ["sts.amazonaws.com"]
  # No thumbprint: AWS verifies GitHub's certificate against its own trusted CAs.
}

# One role per environment, dinogym-deploy-staging and dinogym-deploy-prod.
resource "aws_iam_role" "deploy" {
  for_each = local.envs

  name                 = "dinogym-deploy-${each.key}"
  description          = "Assumed by .github/workflows/deploy.yml to run Terraform in infra/envs/${each.key}."
  max_session_duration = 3600

  # Only a job of this repository that runs in the GitHub environment of the
  # same name. The environment is what carries the protection: prod needs a
  # reviewer and both accept only main, so a branch or a fork cannot deploy.
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Federated = aws_iam_openid_connect_provider.github.arn }
      Action    = "sts:AssumeRoleWithWebIdentity"
      Condition = {
        StringEquals = {
          "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
          "token.actions.githubusercontent.com:sub" = "repo:${local.github_repo}:environment:${each.key}"
        }
      }
    }]
  })
}

# What Terraform needs to manage infra/modules/dinogym for one environment.
# Every resource there is named dinogym-<env>..., so most statements are
# limited to those names: the staging role cannot touch prod, and the reverse.
resource "aws_iam_role_policy" "deploy" {
  for_each = local.envs

  name = "terraform-${each.key}"
  role = aws_iam_role.deploy[each.key].id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      # Terraform state: list the bucket, and read and write only this
      # environment's state file and its lock file (use_lockfile, decision 66).
      {
        Sid      = "StateList"
        Effect   = "Allow"
        Action   = "s3:ListBucket"
        Resource = aws_s3_bucket.state.arn
      },
      {
        Sid    = "StateReadWrite"
        Effect = "Allow"
        Action = ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"]
        Resource = [
          "${aws_s3_bucket.state.arn}/${each.key}/terraform.tfstate",
          "${aws_s3_bucket.state.arn}/${each.key}/terraform.tfstate.tflock",
        ]
      },

      {
        Sid    = "DynamoDBTable"
        Effect = "Allow"
        Action = [
          "dynamodb:CreateTable",
          "dynamodb:UpdateTable",
          "dynamodb:DeleteTable",
          "dynamodb:Describe*",
          "dynamodb:ListTagsOfResource",
          "dynamodb:TagResource",
          "dynamodb:UntagResource",
          "dynamodb:UpdateContinuousBackups",
          "dynamodb:UpdateTimeToLive",
        ]
        Resource = "arn:aws:dynamodb:us-east-1:${local.account_id}:table/dinogym-${each.key}*"
      },

      {
        Sid    = "Lambda"
        Effect = "Allow"
        Action = [
          "lambda:CreateFunction",
          "lambda:UpdateFunctionCode",
          "lambda:UpdateFunctionConfiguration",
          "lambda:DeleteFunction",
          "lambda:Get*",
          "lambda:List*",
          "lambda:AddPermission",
          "lambda:RemovePermission",
          "lambda:TagResource",
          "lambda:UntagResource",
        ]
        Resource = "arn:aws:lambda:us-east-1:${local.account_id}:function:dinogym-${each.key}-*"
      },

      # The Lambda's own role and its inline policy.
      {
        Sid    = "LambdaRole"
        Effect = "Allow"
        Action = [
          "iam:CreateRole",
          "iam:DeleteRole",
          "iam:GetRole",
          "iam:UpdateRole",
          "iam:UpdateAssumeRolePolicy",
          "iam:TagRole",
          "iam:UntagRole",
          "iam:PutRolePolicy",
          "iam:GetRolePolicy",
          "iam:DeleteRolePolicy",
          "iam:ListRolePolicies",
          "iam:ListAttachedRolePolicies",
          "iam:ListInstanceProfilesForRole",
        ]
        Resource = "arn:aws:iam::${local.account_id}:role/dinogym-${each.key}-*"
      },
      # Lambda checks that whoever creates the function may hand it this role.
      # Only the API's role, and only to Lambda.
      {
        Sid       = "PassLambdaRole"
        Effect    = "Allow"
        Action    = "iam:PassRole"
        Resource  = "arn:aws:iam::${local.account_id}:role/dinogym-${each.key}-api"
        Condition = { StringEquals = { "iam:PassedToService" = "lambda.amazonaws.com" } }
      },

      {
        Sid    = "LogGroup"
        Effect = "Allow"
        Action = [
          "logs:CreateLogGroup",
          "logs:DeleteLogGroup",
          "logs:PutRetentionPolicy",
          "logs:DeleteRetentionPolicy",
          "logs:TagResource",
          "logs:UntagResource",
          "logs:ListTagsForResource",
        ]
        Resource = [
          "arn:aws:logs:us-east-1:${local.account_id}:log-group:/aws/lambda/dinogym-${each.key}-*",
          "arn:aws:logs:us-east-1:${local.account_id}:log-group:/aws/lambda/dinogym-${each.key}-*:*",
        ]
      },
      # DescribeLogGroups cannot be limited to one log group: it lists them.
      {
        Sid      = "LogGroupList"
        Effect   = "Allow"
        Action   = "logs:DescribeLogGroups"
        Resource = "*"
      },

      # The secrets' parameters (decision 54). Terraform writes the placeholder
      # and reads the parameter back on every plan.
      {
        Sid    = "Parameters"
        Effect = "Allow"
        Action = [
          "ssm:PutParameter",
          "ssm:GetParameter",
          "ssm:GetParameters",
          "ssm:DeleteParameter",
          "ssm:AddTagsToResource",
          "ssm:RemoveTagsFromResource",
          "ssm:ListTagsForResource",
        ]
        Resource = "arn:aws:ssm:us-east-1:${local.account_id}:parameter/dinogym-${each.key}/*"
      },
      # DescribeParameters cannot be limited to a path: it lists them.
      {
        Sid      = "ParameterList"
        Effect   = "Allow"
        Action   = "ssm:DescribeParameters"
        Resource = "*"
      },

      # API Gateway's ARNs hold the API's random ID, not its name, so this
      # cannot be limited to dinogym-<env>-api: it covers every HTTP API in the
      # account and region. Acceptable in an account that holds only DinoGym.
      {
        Sid    = "ApiGateway"
        Effect = "Allow"
        Action = [
          "apigateway:GET",
          "apigateway:POST",
          "apigateway:PUT",
          "apigateway:PATCH",
          "apigateway:DELETE",
        ]
        Resource = [
          "arn:aws:apigateway:us-east-1::/apis",
          "arn:aws:apigateway:us-east-1::/apis/*",
          "arn:aws:apigateway:us-east-1::/tags/*",
        ]
      },
    ]
  })
}
