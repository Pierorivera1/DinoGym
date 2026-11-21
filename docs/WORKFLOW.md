# Workflow: emulator first

Every change is proven against the AWS Emulator before it reaches real AWS. The aim is to catch wrong AWS usage early and for free, keep control of the budget, and avoid creating services we don't need.

## The four stages

1. **Laptop.** The Go API runs as a normal program, with its AWS calls pointed at the emulator. No AWS account is involved.
2. **Pull request.** GitHub Actions starts the emulator, runs the integration tests against it, and applies, re-plans and destroys the Terraform on it (proven to work with AWS provider 6.22.0, see below). One script, `infra/emulator/check.sh`, run by `.github/workflows/infra.yml` and on the laptop (decision 68).
3. **Merge.** `.github/workflows/deploy.yml` reruns the emulator checks, builds the Lambda zip once, deploys to staging on real AWS through OIDC and calls `/health` there. This covers what the emulator cannot (decision 73).
4. **Approval.** The `prod` GitHub environment requires the owner's approval; then the same zip is deployed to prod and smoke-tested.

## About the AWS Emulator

- Product page: https://aws.amazon.com/products/developer-tools/emulator/
- Source: https://github.com/aws/aws-emulator
- Docs: https://docs.aws.amazon.com/emulator/

It is a local program that serves the AWS APIs. It needs no AWS account and has no fee. The repository was created in September 2026 and is released several times a day, so we pin one version (decision 57).

The findings below were **run and checked on 2026-11-17 with version 1.0.491**. Every command and its real output is in [reports/aws-emulator/](reports/aws-emulator/) (PDF report, screenshots and raw captures). Re-check them when the pinned version is upgraded.

### What works for us

| Need | Result on 1.0.491 |
|---|---|
| DynamoDB: the table from decision 52 with GSI1, conditional writes (decision 51), query by change number | Works. Same errors as AWS: a new table is `CREATING` and writes fail with `ResourceNotFoundException` until it is `ACTIVE`; an older edit gets `ConditionalCheckFailedException` |
| SQS: send, receive, delete, purge | Works |
| S3: bucket, upload, download (CLI and SDK) | Works. Use `127.0.0.1`, not `localhost`, so the bucket goes in the path |
| EventBridge Scheduler: create a schedule, deliver to SQS | Works, and schedules **fire on time**. Creation needs an existing role that Scheduler may assume; delivery needs `sqs:SendMessage` on that role, or it is dropped with `AccessDenied` in the emulator log |
| Lambda: create, configure, mark `Active` | Works for any runtime, so Terraform can manage our function |
| Terraform (AWS provider **6.22.0**): DynamoDB table, SQS queue, S3 bucket, IAM role and policy, schedule | `apply`, a second `plan` with no changes, and `destroy` all work. Probe config: [../infra/emulator/terraform-check/](../infra/emulator/terraform-check/) |
| Terraform for our stack (task 17): the above plus API Gateway HTTP API (route, `$default` stage, throttling), Lambda with JSON logging, CloudWatch log group, SSM SecureString, DynamoDB point-in-time recovery and deletion protection | Same: `apply`, an empty second `plan`, drift reverted, `destroy`. Checked 2026-11-17. Config: [../infra/envs/emulator/](../infra/envs/emulator/) ([../infra/README.md](../infra/README.md)) |
| CloudFront and S3 for shared links (task 23): bucket with origin access control, CloudFront Function, cache and response headers policies, distribution with an S3 and an API Gateway origin | `apply`, an empty second `plan`, `destroy`. The emulator also **serves** the distribution on a port inside its container: the function's rewrite, OAC to S3, the GET-only behaviour, the HTTPS redirect and the response headers all behave, and `check.sh` requests them with `docker exec` (decision 101). Checked 2026-11-18 |
| The web dashboard on the same distribution (task 25): its built files in S3, the function's rewrite of `/`, `/history…`, `/progress…` and `/weight…` to `/app/index.html`, a second response headers policy, an every-method `/auth/*` and `/sync/*` behaviour to API Gateway with a zero-TTL cache policy and the managed AllViewerExceptHostHeader origin request policy | `apply`, an empty second `plan`, `destroy`, and `check.sh` requests the dashboard's addresses, its CSP, its script's cache headers, and a POST to `/auth/*` and a GET to `/sync/*` reaching API Gateway (decision 116). Only staging proves real CloudFront forwarding `Authorization` to the Lambda and Google sign-in under the CSP |

### What does not work

- **Running our Go Lambda.** Go ships on the OS-only runtime `provided.al2023` (a `bootstrap` binary). The emulator runs only Python and Node.js code: invoking the Go function returns `InvalidRuntimeException: The runtime provided.al2023 is not supported by this emulator, which runs the Python and node runtimes.` A Python function invoked the same way runs. Tracked upstream in aws/aws-emulator#3. So API Gateway → Lambda and SQS → Lambda cannot run either.
- **S3 with AWS provider 6.23.0 and later.** Those versions read bucket tags through S3 Control, whose requests always go to `<account-id>.127.0.0.1`, which does not resolve. Every `aws_s3_bucket` fails (aws/aws-emulator#56). We pin the provider to 6.22.0 (decision 58).
- **Permissions in general.** It does not evaluate IAM and resource policies the way AWS does, so it cannot be used for security testing. The exception we saw: Scheduler checks the role's identity policy when it delivers.
- **Load or long-running use.** It is not tested for either. State is in memory and is lost when it stops.

### How we test around the gaps

- The Go code is tested as ordinary Go, with its AWS calls going to the emulator: `go test -p 2 -tags integration ./internal/store/ ./internal/server/ ./internal/awstest/` after `source infra/emulator/env.sh` (decision 65).
- The Lambda path and real permissions are covered by staging on real AWS. CloudFront's configuration is checked on the emulator, but only staging proves real CloudFront and S3, the bucket policy, and the Lambda answering through `/shares/*` (`deploy.yml` requests the page, `assetlinks.json` and a share there).

### What we may claim

The emulator proves that our code and configuration use the AWS APIs correctly. It does not prove reliability, security or performance.

If we find emulator bugs, we report them upstream.

## Running the emulator on this machine

AWS publishes RPM and Debian packages only, and no container image. On Arch, we build our own image from a pinned, checksummed package (decision 57):

```bash
infra/emulator/start.sh          # builds the image once, starts it in the background
source infra/emulator/env.sh     # point this shell's AWS CLI and SDKs at it
aws sts get-caller-identity      # must show "Account": "000000000000"
docker logs -f aws-emulator      # watch requests
docker stop aws-emulator         # stop; deletes every emulated resource
```

- [../infra/emulator/Dockerfile](../infra/emulator/Dockerfile): Amazon Linux 2023 (pinned by digest) plus the emulator RPM (pinned by version and SHA-256).
- [../infra/emulator/start.sh](../infra/emulator/start.sh): port `127.0.0.1:9297` only, 2 GB memory and no swap, telemetry off, stops itself after an hour without an API call.
- [../infra/emulator/env.sh](../infra/emulator/env.sh): the variables from the docs page "Pointing your AWS SDK at AWS Emulator", and it unsets real AWS credentials. Source it only in a shell that must not reach real AWS: `AWS_DISABLE_HOST_PREFIX_INJECTION` breaks some calls on AWS.
- The emulator writes fresh credentials and a self-signed certificate to `~/.aws/emulator/9297/` on every start. They are only valid for that run.

### Upgrading the pinned version

1. Pick a version from `https://downloads.aws.com/aws-emulator/packages/aws-emulator/LTS/versions.json` and read its release notes on GitHub. Security fixes only go into the latest release.
2. Download `aws-emulator-<version>-1-x86_64.rpm` and its `.sig`, import `https://downloads.aws.com/keys/gpg/current.asc`, check the fingerprint is `98BC F336 A3BE 53B7 34E9 67C5 2E5B F49B D940 F624`, and run `gpg --verify`.
3. Update `EMULATOR_VERSION` and the `--checksum` in the Dockerfile, and `VERSION` in `start.sh`.
4. Rerun the checks in the report and the Terraform probe before merging.

Do not build the emulator from source. It needs about 100 GB of disk and a long multi-core compile, which breaks the CPU rule in [../CLAUDE.md](../CLAUDE.md).

## Local resource limits

The laptop overheated during version 1's development, so these apply to all local work:

- Gradle: at most 3 workers, no parallel builds, 2 GB heap (settings in [../CLAUDE.md](../CLAUDE.md)).
- Run only the tests for the code that changed.
- No Android emulator and no instrumented tests locally. They run in GitHub Actions.
- The AWS Emulator container is capped at 2 GB of memory.
- The full test suites run in GitHub Actions, which is free for public repositories.

## Android Studio

The AI builds from the command line with Gradle, which is the same build Android Studio uses. The owner opens the `android/` folder in Android Studio to run the app on a phone or emulator and to view Compose previews.
