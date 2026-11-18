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
  source        = "../../modules/dinogym"
  env           = "staging"
  lambda_zip    = "${path.root}/../../../backend/bin/api.zip"
  reminders_zip = "${path.root}/../../../backend/bin/reminders.zip"
  # The emulator cannot run the reminder Lambda (decision 14), so these are
  # never used; the real values are in envs/staging.
  fcm = {
    project         = "dinogym-emulator"
    audience        = "//iam.googleapis.com/projects/0/locations/global/workloadIdentityPools/dinogym-aws/providers/aws-account"
    service_account = "fcm-sender@dinogym-emulator.iam.gserviceaccount.com"
  }
  # Same as staging (decision 80). Public: it is in every ID token.
  google_client_id = "854486518090-7sc6gs4dmbuo5lgsm9ss9g0p0chedm9a.apps.googleusercontent.com"
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
