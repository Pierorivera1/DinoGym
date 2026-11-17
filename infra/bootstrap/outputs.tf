output "state_bucket" {
  description = "For `terraform init -backend-config=\"bucket=...\"` and the TF_STATE_BUCKET GitHub variable."
  value       = aws_s3_bucket.state.bucket
}

output "deploy_role_arns" {
  description = "Each is the AWS_DEPLOY_ROLE_ARN variable of the matching GitHub environment."
  value       = { for env, role in aws_iam_role.deploy : env => role.arn }
}
