output "api_url" {
  description = "Base URL of the API (no custom domain, decision 23)."
  value       = aws_apigatewayv2_stage.default.invoke_url
}

output "table_name" {
  value = aws_dynamodb_table.main.name
}

output "lambda_name" {
  value = aws_lambda_function.api.function_name
}

output "web_url" {
  description = "Base URL of the shared routine links, https://<distribution>.cloudfront.net (decision 23)."
  value       = "https://${aws_cloudfront_distribution.web.domain_name}"
}
