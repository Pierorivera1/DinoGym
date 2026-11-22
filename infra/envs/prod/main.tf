# The prod stack on real AWS. State is in S3, locked with S3's own lock file
# (decision 66). The bucket is passed at init, created once in task 19:
#   terraform init -backend-config="bucket=<state bucket>"

terraform {
  required_version = "= 1.17.2"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "= 6.22.0"
    }
    grafana = {
      source  = "grafana/grafana"
      version = "= 4.55.0" # decision 121
    }
  }

  backend "s3" {
    key          = "prod/terraform.tfstate"
    region       = "us-east-1"
    use_lockfile = true
    encrypt      = true
  }
}

provider "aws" {
  region = "us-east-1" # decision 53

  default_tags {
    tags = { Project = "dinogym", Env = "prod" }
  }
}

# The Grafana Cloud stack generousfence3427 (decision 121). The token is read
# from GRAFANA_AUTH: the GitHub environment secret GRAFANA_TOKEN in CI, a
# service account token with the Editor role.
provider "grafana" {
  url = "https://generousfence3427.grafana.net"
}

variable "alert_email" {
  description = "Where Grafana's alerts go. Personal data: TF_VAR_alert_email (the GitHub environment secret ALERT_EMAIL), never committed."
  type        = string
  sensitive   = true
}

data "aws_caller_identity" "current" {}

module "dinogym" {
  source        = "../../modules/dinogym"
  env           = "prod"
  lambda_zip    = "${path.root}/../../../backend/bin/api.zip"
  reminders_zip = "${path.root}/../../../backend/bin/reminders.zip"
  protect_data  = true
  # Grafana Cloud's OTLP gateway for the stack generousfence3427 (decision 118).
  # Not secret; the token is read from /dinogym-prod/grafana-token in SSM.
  grafana = {
    otlp_endpoint = "https://otlp-gateway-prod-us-east-6.grafana.net/otlp"
    instance_id   = "1926627"
  }
  # From infra/google's `fcm` output (decision 107). Not secret.
  fcm = {
    project         = "dinogym-3870b"
    audience        = "//iam.googleapis.com/projects/854486518090/locations/global/workloadIdentityPools/dinogym-aws/providers/aws-account"
    service_account = "fcm-sender@dinogym-3870b.iam.gserviceaccount.com"
  }
  # The Web OAuth client ID (decision 80). Public: it is in every ID token.
  google_client_id = "854486518090-7sc6gs4dmbuo5lgsm9ss9g0p0chedm9a.apps.googleusercontent.com"
  # Created by infra/bootstrap. The deploy role cannot create the API role without it.
  permissions_boundary = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:policy/dinogym-prod-boundary"
  # The release app, signed with the release key (docs/RELEASE.md). Public: in every APK.
  app_links = [{
    package_name             = "io.github.pierorivera1.dinogym"
    sha256_cert_fingerprints = ["2C:A1:4F:19:D6:CE:3E:85:8A:E2:B1:43:99:D1:67:28:E3:58:B6:4B:25:41:A2:C4:9B:64:E4:79:00:0D:6D:84"]
  }]
}

# Dashboard and alerts (task 26, decision 121).
module "grafana" {
  source      = "../../modules/grafana"
  env         = "prod"
  alert_email = var.alert_email
  # Live: prod's alerts email the owner.
  alerts_paused = false
}

output "api_url" {
  value = module.dinogym.api_url
}

output "web_url" {
  value = module.dinogym.web_url
}

output "grafana_dashboard_url" {
  value = module.grafana.dashboard_url
}
