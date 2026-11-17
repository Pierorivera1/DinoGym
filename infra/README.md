# Infrastructure

Terraform for the DinoGym backend (decisions 66–67).

| Path | What |
|---|---|
| `modules/dinogym` | One environment: DynamoDB table, Go Lambda, API Gateway HTTP API, SSM parameters |
| `envs/emulator` | The staging stack on the local AWS Emulator, local state |
| `envs/staging`, `envs/prod` | Real AWS, state in S3 |
| `scripts/build-lambda.sh` | Builds `backend/bin/api.zip`, which every root deploys |
| `emulator/` | Starts the AWS Emulator ([../docs/WORKFLOW.md](../docs/WORKFLOW.md)) |

## On the emulator

```bash
infra/emulator/start.sh
source infra/emulator/env.sh
infra/scripts/build-lambda.sh
cd infra/envs/emulator
terraform init
terraform apply
terraform plan      # must say "No changes"
terraform destroy
```

The emulator creates every resource, but cannot run the Lambda, route API Gateway to it, or check IAM. Those are proven in staging.

## On real AWS (from task 19)

The state bucket must exist first. Then, in a shell without `env.sh`:

```bash
infra/scripts/build-lambda.sh
cd infra/envs/staging
terraform init -backend-config="bucket=<state bucket>"
terraform plan
```

The SSM parameters are created as `unset`. Set each real value once; Terraform never reads or overwrites it:

```bash
aws ssm put-parameter --overwrite --type SecureString --name /dinogym-staging/session-signing-key --value "$(openssl rand -base64 48)"
```
