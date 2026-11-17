variable "env" {
  description = "staging or prod. Names every resource and sets the API's defaults."
  type        = string
  validation {
    condition     = contains(["staging", "prod"], var.env)
    error_message = "env must be staging or prod."
  }
}

variable "lambda_zip" {
  description = "Path to the zip holding the Go binary named bootstrap (infra/scripts/build-lambda.sh)."
  type        = string
}

variable "protect_data" {
  description = "Deletion protection and point-in-time recovery on the table. On for prod."
  type        = bool
  default     = false
}

variable "throttle_rate" {
  description = "Steady requests per second the API accepts before answering 429. Caps the bill if a client loops."
  type        = number
  default     = 10
}

variable "throttle_burst" {
  description = "Requests the API accepts at once above the steady rate."
  type        = number
  default     = 20
}

variable "permissions_boundary" {
  description = "ARN of the managed policy that caps the API role (dinogym-<env>-boundary, from infra/bootstrap). Null on the emulator, which has no bootstrap."
  type        = string
  default     = null
}
