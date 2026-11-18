# DinoGym

An offline-first Android workout tracker for gym and calisthenics training, with a serverless AWS backend for accounts, sync, shareable routines and reminders.

## Versions

| Version | What | Needs AWS |
|---|---|---|
| [2.0.0](https://github.com/Pierorivera1/DinoGym/releases/tag/v2.0.0) | The Android app, fully offline: training, history, programs, body weight. [Download the APK](https://github.com/Pierorivera1/DinoGym/releases/download/v2.0.0/DinoGym-2.0.0.apk) | No |
| 2.x cloud (in progress) | Adds the serverless AWS backend: sign-in, sync between devices, shareable routines, reminders, a web dashboard | Yes |

The cloud version stays offline first: every feature works without a connection, and the app keeps working if the backend is turned off. The AWS side is planned to run until February 2027.

**Status:** the app is in daily use (tasks 1–11). The cloud backend is being built: the API contract is [api/openapi.yaml](api/openapi.yaml), the Go API in [backend/](backend/) is checked against the AWS Emulator on every pull request, and merges to `main` deploy it to staging and, after approval, prod on real AWS. Progress is in [docs/TASKS.md](docs/TASKS.md).

| Area | Stack |
|---|---|
| Android | Kotlin, Jetpack Compose, Room, WorkManager, Hilt |
| Backend | Go on AWS Lambda, API Gateway, DynamoDB, SQS, EventBridge Scheduler |
| Web | React + TypeScript on S3 and CloudFront |
| Infrastructure | Terraform, GitHub Actions with OIDC, AWS Emulator for pre-deploy checks |
| Observability | OpenTelemetry, Grafana Cloud, Firebase Crashlytics |

Start with [docs/PROJECT.md](docs/PROJECT.md), then [docs/DECISIONS.md](docs/DECISIONS.md) for why each choice was made.

## License

[MIT](LICENSE)
