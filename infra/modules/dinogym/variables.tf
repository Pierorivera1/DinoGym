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

variable "reminders_zip" {
  description = "Path to the zip holding the reminder Lambda's binary named bootstrap (infra/scripts/build-lambda.sh)."
  type        = string
}

variable "fcm" {
  description = <<-EOT
    Where the reminder Lambda sends (decision 107): the Firebase project id, the
    Google workload identity provider it exchanges its AWS credentials at
    (//iam.googleapis.com/projects/<number>/locations/global/workloadIdentityPools/<pool>/providers/<provider>),
    and the service account it then acts as. From infra/google's outputs. Not secret.
  EOT
  type = object({
    project         = string
    audience        = string
    service_account = string
  })
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

variable "google_client_id" {
  description = "Web OAuth client ID that Google ID tokens must be issued to (decision 80). Not secret: it is in every ID token."
  type        = string
}

variable "app_links" {
  description = <<-EOT
    Android apps that may open this environment's /r/* links directly (App Links,
    decision 99): package name and the SHA-256 fingerprints of its signing
    certificates. The first package is also the one the web page's
    "Open in DinoGym" button opens. Fingerprints are public: they are in every APK.
  EOT
  type = list(object({
    package_name             = string
    sha256_cert_fingerprints = list(string)
  }))
  validation {
    condition     = length(var.app_links) > 0
    error_message = "app_links needs at least one app."
  }
}

variable "releases_url" {
  description = "Where people without the app get it: the GitHub Releases page (decision 8)."
  type        = string
  default     = "https://github.com/Pierorivera1/DinoGym/releases/latest"
}

variable "grafana" {
  description = <<-EOT
    Grafana Cloud's OTLP gateway, where both Lambdas send traces, metrics and
    logs (decision 118): the endpoint (https://otlp-gateway-<zone>.grafana.net/otlp)
    and the stack's instance id. Neither is secret; the token is read from
    /dinogym-<env>/grafana-token in SSM, which must exist before this is set.
    null: telemetry is off and nothing reads the token.
  EOT
  type = object({
    otlp_endpoint = string
    instance_id   = string
  })
  default = null
}
