variable "budget_email" {
  description = "Where AWS Budgets sends the cost alerts. Personal data: pass it with -var or TF_VAR_budget_email, never in a committed file."
  type        = string
  nullable    = false
}

variable "monthly_budget_usd" {
  description = "The monthly cost the alerts measure against (decision 6)."
  type        = string
  default     = "10"
}

variable "emulator" {
  description = "True only when applying to the local AWS Emulator, which is reached by IP and so needs S3 buckets in the path."
  type        = bool
  default     = false
}
