terraform {
  required_version = "= 1.17.2" # decision 18: same Terraform on the laptop and in CI
  required_providers {
    aws = {
      source = "hashicorp/aws"
      # 6.23.0 and later break S3 on the AWS Emulator (aws/aws-emulator#56). Decision 58.
      version = "= 6.22.0"
    }
  }
}
