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

data "aws_caller_identity" "current" {}

module "dinogym" {
  source       = "../../modules/dinogym"
  env          = "prod"
  lambda_zip   = "${path.root}/../../../backend/bin/api.zip"
  protect_data = true
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

output "api_url" {
  value = module.dinogym.api_url
}

output "web_url" {
  value = module.dinogym.web_url
}
