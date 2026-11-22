variable "env" {
  description = "staging or prod. Every query filters on deployment.environment.name = env."
  type        = string
  validation {
    condition     = can(regex("^[a-z][a-z0-9-]*$", var.env))
    error_message = "env must be lowercase letters, digits and dashes."
  }
}

variable "alert_email" {
  description = "Where the alerts go. Personal data: pass it with TF_VAR_alert_email (the GitHub environment secret ALERT_EMAIL), never in a committed file."
  type        = string
  sensitive   = true
}

variable "alerts_paused" {
  description = "Create the alert rules paused. Staging does, so a rule change is applied there first without emailing about staging traffic (decision 121)."
  type        = bool
  default     = false
}
