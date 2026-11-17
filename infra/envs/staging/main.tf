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
  source       = "../../modules/dinogym"
  env          = "staging"
  lambda_zip   = "${path.root}/../../../backend/bin/api.zip"
  protect_data = false
  # Created by infra/bootstrap. The deploy role cannot create the API role without it.
  permissions_boundary = "arn:aws:iam::${data.aws_caller_identity.current.account_id}:policy/dinogym-staging-boundary"
}

output "api_url" {
  value = module.dinogym.api_url
}
