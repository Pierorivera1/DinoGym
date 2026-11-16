# Workflow: emulator first

Every change is proven against the AWS Emulator before it reaches real AWS. The aim is to catch wrong AWS usage early and for free, keep control of the budget, and avoid creating services we don't need.

## The four stages

1. **Laptop.** The Go API runs as a normal program, with its AWS calls pointed at the emulator. No AWS account is involved.
2. **Pull request.** GitHub Actions starts the emulator, runs the integration tests against it, and applies the Terraform to it (if Terraform proves to work with it, see below).
3. **Merge.** Deploy to staging on real AWS and run smoke tests there. This covers what the emulator cannot.
4. **Approval.** Deploy to prod, behind a manual approval gate.

## About the AWS Emulator

- Product page: https://aws.amazon.com/products/developer-tools/emulator/
- Source: https://github.com/aws/aws-emulator
- Docs: https://docs.aws.amazon.com/emulator/

It is a local program that serves the AWS APIs. It needs no AWS account and has no fee. The repository was created in September 2026 and is updated many times a day, so we pin one version.

The findings below come from reading the docs and the repository's own list of known gaps on 2026-11-15 (release v1.0.385). **Nothing has been run yet.** Re-check them when the emulator is installed, because gaps close quickly.

### What it can do

- Create, describe, update and delete resources, with the same validation errors as AWS. This is where it matches AWS most closely.
- Store and read data in DynamoDB, SQS and S3.
- Run in CI with no AWS account and no credentials.

### What it cannot do

- **Run our Go Lambda.** Its built-in Lambda runtime handles Node.js (Python partly). Open gap reports say other deployment packages cannot execute.
- **Route requests to our function.** API Gateway forwarding to the function and SQS triggering it both depend on running the function.
- **Check permissions.** It does not evaluate IAM policies. The docs say not to use it for security testing.
- **Load or long-running use.** It is not tested for either. State is in memory and is lost when it stops.

### Not yet known

- **Terraform.** The docs say support for infrastructure-as-code tools "varies by service". Test our Terraform against it early. If it does not work, stage 2 keeps the integration tests and drops the Terraform apply.

### What we may claim

The emulator proves that our code and configuration use the AWS APIs correctly. It does not prove reliability, security or performance. The staging stage on real AWS exists to cover permissions, the API Gateway to Lambda path, and CloudFront.

If we find emulator bugs, we report them upstream.

## Running the emulator on this machine

AWS publishes RPM and Debian packages only. On Arch, use the documented Docker route:

```dockerfile
FROM public.ecr.aws/amazonlinux/amazonlinux:2023-minimal
ADD https://downloads.aws.com/aws-emulator/packages/aws-emulator/LTS/latest/aws-emulator-x86_64.rpm /tmp/aws-emulator.rpm
RUN rpm -i /tmp/aws-emulator.rpm && rm /tmp/aws-emulator.rpm \
    && mkdir -p /home/emulator && chown 1000 /home/emulator
USER 1000
ENV HOME=/home/emulator
EXPOSE 9297
ENTRYPOINT ["aws-emulator", "-b", "0.0.0.0"]
```

```bash
docker build -t aws-emulator .
mkdir -p ~/.aws/emulator
PORT=9297
docker run --rm --user "$(id -u):$(id -g)" --memory=2g --memory-swap=2g \
  -p 127.0.0.1:$PORT:$PORT \
  -e HOME="$HOME" -v "$HOME/.aws/emulator:$HOME/.aws/emulator" \
  aws-emulator -p $PORT
```

Notes:

- It serves HTTPS on `127.0.0.1:9297` with a self-signed certificate, and writes its credentials and certificate under `~/.aws/emulator`.
- Point the AWS CLI and SDKs at it with environment variables, as described in the docs page "Pointing your AWS SDK at AWS Emulator".
- Keep the port bound to `127.0.0.1`, never to all interfaces.
- Telemetry is on by default and can be turned off (`--telemetry off` or `EMULATOR_TELEMETRY`).
- Do not build the emulator from source. It needs about 100 GB of disk and a long multi-core compile, which breaks the CPU rule in [../CLAUDE.md](../CLAUDE.md).
- AWS also ships an `aws-emulator` skill for AI coding agents that installs, starts and connects the emulator, and checks whether a project is expected to work on it.

## Local resource limits

The laptop overheated during version 1's development, so these apply to all local work:

- Gradle: at most 3 workers, no parallel builds, 2 GB heap (settings in [../CLAUDE.md](../CLAUDE.md)).
- Run only the tests for the code that changed.
- No Android emulator and no instrumented tests locally. They run in GitHub Actions.
- The AWS Emulator container is capped at 2 GB of memory.
- The full test suites run in GitHub Actions, which is free for public repositories.

## Android Studio

The AI builds from the command line with Gradle, which is the same build Android Studio uses. The owner opens the `android/` folder in Android Studio to run the app on a phone or emulator and to view Compose previews.
