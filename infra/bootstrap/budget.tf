# Decision 6: the account must stay under $10 a month. An email at half the
# budget, at the full budget, and when AWS forecasts the month will pass it.
# AWS Budgets does not charge for an account's first two budgets.

resource "aws_budgets_budget" "monthly" {
  name         = "dinogym-monthly"
  budget_type  = "COST"
  limit_amount = var.monthly_budget_usd
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  dynamic "notification" {
    for_each = [
      { type = "ACTUAL", percent = 50 },
      { type = "ACTUAL", percent = 100 },
      { type = "FORECASTED", percent = 100 },
    ]
    content {
      comparison_operator        = "GREATER_THAN"
      notification_type          = notification.value.type
      threshold                  = notification.value.percent
      threshold_type             = "PERCENTAGE"
      subscriber_email_addresses = [var.budget_email]
    }
  }
}
