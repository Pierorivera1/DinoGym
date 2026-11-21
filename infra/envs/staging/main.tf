# The staging stack on real AWS. State is in S3, locked with S3's own lock file
# (decision 66). The bucket is passed at init, created once in task 19:
#   terraform init -backend-config="bucket=<state bucket>"

terraform {
  required_version = "= 1.17.2"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "= 6.22.0"
    }
  }

  backend "s3" {
    key          = "staging/terraform.tfstate"
    region       = "us-east-1"
    use_lockfile = true
    encrypt      = true
  }
}

provider "aws" {
  region = "us-east-1" # decision 53

  default_tags {
    tags = { Project = "dinogym", Env = "staging" }
  }
}

data "aws_caller_identity" "current" {}

module "dinogym" {
  source        = "../../modules/dinogym"
  env           = "staging"
  lambda_zip    = "${path.root}/../../../backend/bin/api.zip"
  reminders_zip = "${path.root}/../../../backend/bin/reminders.zip"
  protect_data  = false
  # Grafana Cloud's OTLP gateway for the stack generousfence3427 (decision 118).
  # Not secret; the token is read from /dinogym-staging/grafana-token in SSM.
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
  permissions_boundary = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:policy/dinogym-staging-boundary"
  # Debug builds talk to staging (decision 81), so staging's links open the
  # debug app. Both laptop debug keys (decision 85). Public: in every APK.
  app_links = [{
    package_name = "io.github.pierorivera1.dinogym.debug"
    sha256_cert_fingerprints = [
      "1C:17:F4:61:4A:14:F8:AE:9A:79:84:9B:F0:BB:97:06:D1:F9:4D:2E:30:3C:1C:AF:65:DB:31:30:94:CB:5E:79", # ~/.android/debug.keystore
      "3B:77:9E:78:9E:69:F2:CA:C6:28:9B:69:D2:63:37:DB:96:70:6F:F4:AD:C0:48:96:53:54:7F:EC:32:EE:33:B2", # Android Studio's debug key
    ]
  }]
}

output "api_url" {
  value = module.dinogym.api_url
}

output "web_url" {
  value = module.dinogym.web_url
}
