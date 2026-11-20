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
| Networking | Ktor 3 client on OkHttp, generated from the OpenAPI contract into `android/api-client` (OpenAPI Generator, decisions 60 and 81) |
| Sign-in | Sign in with Google through Android Credential Manager (`credentials`, `credentials-play-services-auth`, `googleid`); the session is kept encrypted with a Keystore key (decisions 81–83) |
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
| Database | DynamoDB, through the AWS SDK for Go v2; one table with one sync index (decisions 52 and 65) |
| Files | S3 |
| Queues | SQS |
| Scheduled jobs | EventBridge Scheduler: one schedule per user and reminder kind, made by the API in the group `dinogym-<env>` (decision 105) |
| Auth | The backend verifies the Google ID token with `coreos/go-oidc` and issues its own sessions: HS256 access tokens (`golang-jwt/jwt`) and single-use refresh tokens in DynamoDB (decisions 79–80) |
| Secrets | SSM Parameter Store, SecureString, standard tier; created by the owner, not Terraform (decision 69) |
| Sync | Newest edit per row wins; devices pull by a server change number |
| API contract | OpenAPI 3.0.3 in `api/openapi.yaml`, the source for the Kotlin client and the Go server (oapi-codegen); linted with Redocly CLI in CI (decisions 60–61) |

## Web

| Concern | Choice |
|---|---|
| Language | TypeScript |
| Framework | React |
| Build and tests | Vite, Vitest, npm; Node pinned in `web/dashboard/.nvmrc`; contract types from `openapi-typescript` (decision 111) |
| Hosting | S3 behind CloudFront |
| Pages | Logged-in dashboard (React, task 25), and public pages for shared routines. The shared routine page is the exception: one static HTML file with plain JavaScript and CSS and no build step (`web/share/`, decision 98) |
| Edge logic | One CloudFront Function serves the shared routine page for every `/r/<id>` link (decision 100) |

## Infrastructure and delivery

| Concern | Choice |
|---|---|
| Infrastructure as code | Terraform 1.17.2: one module, one root per environment, state in S3 locked with S3's own lock file (decision 66); AWS provider pinned to 6.22.0 (decision 58) |
| Environments | Emulator, staging (real AWS), prod (real AWS) |
| Region | `us-east-1` |
| Google Cloud access (FCM) | Workload identity federation from the reminder Lambda's AWS role, no key; `golang.org/x/oauth2/google/externalaccount` and plain HTTPS to FCM v1, no Firebase SDK on the server. The Google side is the Terraform root `infra/google` (Google provider 8.10.0), applied by hand (decision 107) |
| CI/CD | GitHub Actions |
| AWS access from CI | OIDC, no stored keys: one deploy role per GitHub environment, with a permissions boundary on the roles it creates (decisions 70–71) |
| Local AWS | AWS Emulator 1.0.491, run in Docker from a pinned image (see [WORKFLOW.md](WORKFLOW.md)); Go integration tests run against it (decisions 59 and 65) |
| Cost control | AWS Budgets: $10 a month, email alerts (decision 72); API throttling (decision 67) |
| Domain | None; API Gateway and CloudFront default URLs |

## Observability

| Concern | Choice |
|---|---|
| Instrumentation | OpenTelemetry Go SDK in the backend, exported over OTLP/HTTP and flushed after every Lambda invocation (decision 64); AWS calls traced with `otelaws` (decision 65) |
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
