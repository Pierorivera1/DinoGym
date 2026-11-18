# Applied once, by hand, by the owner (task 19). Creates what CI needs before
# it can deploy: the state bucket for envs/staging and envs/prod (decision 66),
# GitHub's OIDC provider and one deploy role per environment (no long-lived
# keys, CLAUDE.md), and the $10 monthly budget alert (decision 6).
# The exact commands, including how this root's own state moves into the
# bucket it creates, are in infra/README.md.

terraform {
  required_version = "= 1.17.2" # decision 18
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "= 6.22.0" # decision 58
    }
  }

  # After the first apply, this root's state lives in the bucket it created,
  # under its own key. The first apply keeps state on the laptop through a
  # git-ignored local_override.tf (infra/README.md).
  backend "s3" {
    key          = "bootstrap/terraform.tfstate"
    region       = "us-east-1"
    use_lockfile = true
    encrypt      = true
  }
}

provider "aws" {
  region = "us-east-1" # decision 53
  # Only the emulator needs the bucket in the path (it is reached by IP).
  s3_use_path_style = var.emulator

  default_tags {
    tags = { Project = "dinogym", Env = "bootstrap" }
  }
}

data "aws_caller_identity" "current" {}

locals {
  account_id   = data.aws_caller_identity.current.account_id
  state_bucket = "dinogym-tfstate-${local.account_id}"
  # GitHub sends immutable subjects for this repository: owner and repository
  # IDs next to the names (gh api repos/Pierorivera1/DinoGym/actions/oidc/customization/sub).
  # The IDs survive a rename and are not reused by a new repository with the
  # same name, so they are what the deploy roles trust (decision 77).
  github_sub_prefix = "repo:Pierorivera1@176773497/DinoGym@1460291141"
  envs              = toset(["staging", "prod"])
}
