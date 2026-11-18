# The staging stack applied to the local AWS Emulator (docs/WORKFLOW.md).
# Run only in a shell where infra/emulator/env.sh is sourced: the provider then
# reads the endpoint, credentials and certificate from the environment.
# State stays on this machine; the emulator forgets everything when it stops.

terraform {
  required_version = "= 1.17.2"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "= 6.22.0"
    }
  }
}

provider "aws" {
  region = "us-east-1"
  # The emulator is reached by IP, so S3 must put the bucket in the path.
  s3_use_path_style = true

  default_tags {
    tags = { Project = "dinogym", Env = "emulator" }
  }
}

module "dinogym" {
  source     = "../../modules/dinogym"
  env        = "staging"
  lambda_zip = "${path.root}/../../../backend/bin/api.zip"
  # Same as staging (decision 80). Public: it is in every ID token.
  google_client_id = "854486518090-7sc6gs4dmbuo5lgsm9ss9g0p0chedm9a.apps.googleusercontent.com"
}

output "api_url" {
  value = module.dinogym.api_url
}
