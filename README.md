# DinoGym

An offline-first Android workout tracker for gym and calisthenics training, with a serverless AWS backend for accounts, sync, shareable routines and reminders.

This is version 2, a full rebuild. Version 1 lives at [Old-DinoGym](https://github.com/Pierorivera1/Old-DinoGym).

**Status:** planning done, app build starting. The plan is in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and the design in [docs/DESIGN.md](docs/DESIGN.md).

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
