# Stack

The reasons for each choice are in [DECISIONS.md](DECISIONS.md).

## Android app

| Concern | Choice |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose with Material 3 |
| Navigation | Navigation 3 |
| Local storage | Room 3 with the bundled SQLite driver (replaces version 1's JSON file) |
| Background sync | WorkManager |
| Dependency injection | Hilt |
| Networking | Ktor 3 client, generated from the OpenAPI contract into `android/api-client` (OpenAPI Generator, decision 60) |
| Sign-in | Google one-tap through Android Credential Manager |
| Push | Firebase Cloud Messaging |
| Crash reports | Firebase Crashlytics |
| Languages | English and Spanish |
| Distribution | Signed release APK on GitHub Releases |

## Backend

| Concern | Choice |
|---|---|
| Language | Go |
| Compute | AWS Lambda, deployed as a zipped binary (no Docker image). The same binary runs as a local HTTP server (decision 63) |
| HTTP entry | API Gateway (HTTP API) |
| Database | DynamoDB |
| Files | S3 |
| Queues | SQS |
| Scheduled jobs | EventBridge Scheduler (reminders, weekly summaries) |
| Auth | The backend verifies the Google ID token and issues its own sessions |
| Secrets | SSM Parameter Store, SecureString, standard tier |
| Sync | Newest edit per row wins; devices pull by a server change number |
| API contract | OpenAPI 3.0.3 in `api/openapi.yaml`, the source for the Kotlin client and the Go server (oapi-codegen); linted with Redocly CLI in CI (decisions 60–61) |

## Web

| Concern | Choice |
|---|---|
| Language | TypeScript |
| Framework | React |
| Hosting | S3 behind CloudFront |
| Pages | Logged-in dashboard, and public pages for shared routines |

## Infrastructure and delivery

| Concern | Choice |
|---|---|
| Infrastructure as code | Terraform, remote state in S3; AWS provider pinned to 6.22.0 (decision 58) |
| Environments | Emulator, staging (real AWS), prod (real AWS) |
| Region | `us-east-1` |
| Google Cloud access (FCM) | Workload identity federation from the Lambda's AWS role, no key |
| CI/CD | GitHub Actions |
| AWS access from CI | OIDC, no stored keys |
| Local AWS | AWS Emulator 1.0.491, run in Docker from a pinned image (see [WORKFLOW.md](WORKFLOW.md)) |
| Cost control | AWS budget alarm |
| Domain | None; API Gateway and CloudFront default URLs |

## Observability

| Concern | Choice |
|---|---|
| Instrumentation | OpenTelemetry Go SDK in the backend, exported over OTLP/HTTP and flushed after every Lambda invocation (decision 64) |
| Backend for traces, metrics, logs | Grafana Cloud free tier |
| Dashboards and alerts | Defined in Terraform |
| App crashes | Firebase Crashlytics |
| Lambda logs | CloudWatch Logs, 7-day retention, WARN level in prod (a safety net; detail is in Grafana) |

## Repository layout

One repository holding everything. Planned top-level folders:

```
android/   the Android app
backend/   the Go API and workers
web/       the React site
infra/     Terraform
docs/      these documents
.github/   workflows
```

## Docker

Docker is not used for deployment. Its only job is running the AWS Emulator locally, because AWS publishes only RPM and Debian packages and this machine runs Arch.
