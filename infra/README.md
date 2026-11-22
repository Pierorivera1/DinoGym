# Infrastructure

Terraform for the DinoGym backend (decisions 66–67).

| Path | What |
|---|---|
| `modules/dinogym` | One environment: DynamoDB table, the API Lambda, API Gateway HTTP API, access to the SSM secrets, the share page (task 23), and the reminder Lambda with its schedule group and Scheduler role (task 24) |
| `modules/grafana` | One environment's Grafana Cloud dashboard, email contact point and alert rules (task 26, decision 121) |
| `envs/emulator` | The staging stack on the local AWS Emulator, local state |
| `envs/staging`, `envs/prod` | Real AWS, state in S3, deployed by `.github/workflows/deploy.yml` |
| `bootstrap` | Applied once by hand: the state bucket, GitHub OIDC and the two deploy roles, the $10 budget alert |
| `google` | Applied once by hand: the Google workload identity pool and the FCM sender service account for the reminder Lambdas (decision 107) |
| `scripts/build-lambda.sh` | Builds `backend/bin/api.zip` and `backend/bin/reminders.zip`, which every root deploys |
| `emulator/` | Starts the AWS Emulator ([../docs/WORKFLOW.md](../docs/WORKFLOW.md)) |

## On the emulator

The pull request checks (`.github/workflows/infra.yml`) are one script, which the laptop runs the same way: Terraform format and validate, the Go integration tests, then apply, an empty re-plan and destroy of `envs/emulator`.

```bash
infra/emulator/start.sh      # once, if it is not up
infra/emulator/check.sh
```

The emulator creates every resource, but cannot run the Lambda, route API Gateway to it, or check IAM. Those are proven in staging.

## Bootstrap (once, done in task 19)

`bootstrap/` creates what CI needs before it can deploy anything:

- `dinogym-tfstate-<account-id>`: the state bucket for every root. Versioned (old versions kept 90 days), SSE-S3, public access blocked, plain HTTP denied. It cannot be deleted while it holds state.
- GitHub's OIDC provider and two roles, `dinogym-deploy-staging` and `dinogym-deploy-prod`. Each trusts only a job of `Pierorivera1/DinoGym` (matched by its owner and repository IDs, decision 77) running in the GitHub environment of the same name, and may manage only resources named `dinogym-<env>…` plus its own state file. API Gateway is the exception: its ARNs hold random IDs, not names, so both roles can manage any HTTP API in the account. Since task 23 they also manage the web bucket `dinogym-<env>-web-*` and its objects, and CloudFront, which has the same random-ID problem, so both roles can manage any CloudFront resource in the account (decision 100).
- `dinogym-<env>-boundary`: a permissions boundary allowing only what the environment's roles need: their logs, their table, reading the two secrets, and since task 24 the reminder schedules in `dinogym-<env>`, passing the Scheduler role and invoking the reminder function. A deploy role may create a role or add permissions to one only if the role carries this boundary, may never remove a boundary, and cannot change the boundaries or the deploy roles. So a deploy role cannot make itself a more powerful role. `envs/staging` and `envs/prod` attach it to the API role; `envs/emulator` does not.
- `dinogym-monthly`: an AWS Budgets alert at $10 a month that emails at 50% and 100% actual, and at 100% forecast. The first two budgets in an account are free.

### Signing in on the laptop

Use the IAM user `piero-admin-dinogym` (AdministratorAccess, MFA), never root (decision 74). In a shell where `infra/emulator/env.sh` was not sourced (`echo "$AWS_ENDPOINT_URL"` prints nothing):

```bash
aws login --profile dinogym                      # opens the browser; region us-east-1
aws sts get-caller-identity --profile dinogym    # Arn must end in :user/piero-admin-dinogym
eval "$(aws configure export-credentials --profile dinogym --format env)"
export AWS_REGION=us-east-1
```

The pinned AWS provider (6.22.0) cannot read an `aws login` session from the profile, so the `export-credentials` line hands Terraform short-lived keys in that shell's environment only (decision 75). They expire within the hour; run it again when Terraform says the credentials expired. Nothing is written to disk.

### First apply

Terraform cannot keep its state in a bucket it has not created yet, so the first apply keeps state on the laptop, then moves it into the new bucket under `bootstrap/terraform.tfstate`. In the signed-in shell:

```bash
cd infra/bootstrap
printf 'terraform {\n  backend "local" {}\n}\n' > local_override.tf   # git-ignored; overrides the S3 backend
terraform init
export TF_VAR_budget_email='you@example.com'   # never committed: *.tfvars is git-ignored too
terraform apply
rm local_override.tf
terraform init -migrate-state -backend-config="bucket=dinogym-tfstate-$(aws sts get-caller-identity --query Account --output text)"   # answer yes
terraform plan                                  # must say "No changes"
rm terraform.tfstate terraform.tfstate.backup   # the bucket holds it now; the local copy holds the email
```

Later changes: `terraform init -backend-config="bucket=dinogym-tfstate-<account-id>"`, then `plan` and `apply` with `TF_VAR_budget_email` set.

## On real AWS (from task 19)

After the bootstrap, in a shell signed in as above:

```bash
infra/scripts/build-lambda.sh
cd infra/envs/staging
terraform init -backend-config="bucket=dinogym-tfstate-<account-id>"
terraform plan
```

Terraform does not create the secrets (decision 54): the AWS provider would read each decrypted value into state. It only lets the Lambda read `/dinogym-<env>/session-signing-key` and `/dinogym-<env>/grafana-token`. Create each one with the AWS CLI before the code reads it (tasks 20 and 26):

```bash
aws ssm put-parameter --type SecureString --name /dinogym-staging/session-signing-key --value "$(openssl rand -base64 48)"
```

The Grafana token is a Grafana Cloud access policy token with the `metrics:write`, `logs:write` and `traces:write` scopes (grafana.com → the stack → OpenTelemetry → Configure). Read it from the terminal so it stays out of shell history:

```bash
read -rs GTOKEN && aws ssm put-parameter --type SecureString --name /dinogym-staging/grafana-token --value "$GTOKEN"; unset GTOKEN
```

The parameter must exist before an environment sets `grafana` in its `main.tf`: from then on both Lambdas read it at cold start and fail to start without it (decision 118).

Add `--overwrite` to change a value later. A changed value reaches a Lambda only at its next cold start, such as after a deploy that changes the function.

## Google side of reminders (task 24)

`google/` lets the reminder Lambdas send FCM messages without a Google key (decisions 55 and 107): a workload identity pool in the Firebase project `dinogym-3870b` that accepts only the roles `dinogym-staging-reminders` and `dinogym-prod-reminders` of our AWS account, and the service account `fcm-sender`, which may only send FCM messages. Its state is in the bootstrap's bucket, so the shell needs the AWS credentials from "Signing in on the laptop" as well as Google's:

```bash
gcloud auth application-default login
cd infra/google
terraform init -backend-config="bucket=dinogym-tfstate-<account-id>"
terraform apply -var aws_account_id=<account-id>
terraform output fcm      # matches the fcm block in envs/staging and envs/prod
```

## Deploys from CI

`.github/workflows/deploy.yml` runs on every push to `main` that touches `api/`, `backend/`, `infra/` or the workflow, and by hand from the Actions tab:

1. **checks** runs `infra.yml`, the same emulator checks as a pull request. `main` has no branch protection, so this is what stops an unchecked merge from deploying.
2. **build** builds `api.zip` and `reminders.zip` once and uploads them as an artifact.
3. **staging** signs in as `dinogym-deploy-staging` through OIDC, runs `terraform plan -out` and applies that plan in `envs/staging`, then calls `<api_url>/health` until it answers 200 (about a minute at most).
4. **prod** waits for the owner's approval on the `prod` GitHub environment, then does the same in `envs/prod` with the same zip.

Each environment deploys one run at a time, and a running apply is never cancelled. Until the repository variable `DEPLOY_ENABLED` is `true`, every job is skipped.

GitHub settings the workflow reads:

| Variable | Level | Value |
|---|---|---|
| `DEPLOY_ENABLED` | repository | `true` once the bootstrap is applied |
| `TF_STATE_BUCKET` | repository | the bootstrap's `state_bucket` output |
| `AWS_DEPLOY_ROLE_ARN` | `staging` and `prod` environments | that environment's role from `deploy_role_arns` |

Both environments accept deployments from `main` only, and `prod` requires the owner as reviewer. The roles trust the environment, so these settings are what stop a branch from deploying.

## Grafana dashboard and alerts (task 26)

`modules/grafana` puts one dashboard and three alert rules per environment in the Grafana Cloud stack `generousfence3427`. `envs/staging` and `envs/prod` call it, so `deploy.yml` changes staging's first and prod's after approval. Staging's rules are created paused; prod's email the owner (decision 121). `envs/emulator` has no Grafana.

| Rule | Fires when |
|---|---|
| API 5xx responses | any server span with status error (otelhttp's 5xx) in the last 15 minutes |
| API p95 latency over 2 s | the p95 handler time over 30 minutes stays above 2 s for 5 minutes |
| Reminder function failures | a `reminder fire` span failed in the last hour (no phone reached; Scheduler retries) |

The queries read Grafana's span metrics (`traces_spanmetrics_*`), which Grafana Cloud derives from our traces, not the API's own `http_server_request_duration_seconds`: see decision 121 for why.

The provider signs in with a Grafana service account token from `GRAFANA_AUTH`. In CI that is the environment secret `GRAFANA_TOKEN`, and the alert address is `ALERT_EMAIL`, passed as `TF_VAR_alert_email`:

| Secret | Level | Value |
|---|---|---|
| `GRAFANA_TOKEN` | `staging` and `prod` environments | token of the service account `github-actions` (role Editor, expires 2027-02-01) |
| `ALERT_EMAIL` | `staging` and `prod` environments | where alerts go; never committed. Grafana Cloud refuses an address that is not a member of the organization |

Set up once by hand, because a folder created through the API grants nothing to the account that made it (decision 121):

1. Grafana → Administration → Users and access → Service accounts → Add: `github-actions`, role **Editor**. Add a token and store it with `gh secret set GRAFANA_TOKEN --env staging` and `--env prod`.
2. Create the folders `dinogym-staging` and `dinogym-prod` (uid = title slug):
   ```bash
   read -rs GTOKEN
   for e in staging prod; do
     curl -s -H "Authorization: Bearer $GTOKEN" -H 'Content-Type: application/json' \
       -X POST https://generousfence3427.grafana.net/api/folders -d "{\"uid\":\"dinogym-$e\",\"title\":\"DinoGym $e\"}"
   done; unset GTOKEN
   ```
3. On each folder: Folder actions → Manage permissions → Add a permission → Service account `github-actions` → **Edit**.

Running a plan on the laptop needs the token and an address in the shell: `read -rs GRAFANA_AUTH && export GRAFANA_AUTH TF_VAR_alert_email=...`.

## Bootstrap on the emulator

This proves the bootstrap's AWS calls, not its permissions: the emulator does not evaluate IAM, so the deploy roles' policies are first tested by the first staging deploy.

```bash
source infra/emulator/env.sh
cd infra/bootstrap
printf 'terraform {\n  backend "local" {}\n}\n' > local_override.tf
terraform init
export TF_VAR_budget_email=test@example.com
terraform apply -var emulator=true
terraform plan -var emulator=true        # must say "No changes"
terraform destroy -var emulator=true
rm local_override.tf terraform.tfstate*
```

`emulator=true` only puts S3 buckets in the path, since the emulator is reached by IP. The state migration also works there (`-backend-config=use_path_style=true`); move back to local state before `destroy`, and delete the bucket's object versions first, since a bucket holding state cannot be deleted.
