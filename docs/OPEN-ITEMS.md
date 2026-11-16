# Open items

As of 2026-11-16. Remove an item when it is done or decided, and record decisions in [DECISIONS.md](DECISIONS.md).

## The owner will set up

- [x] Install Go (`pacman`)
- [x] Install Terraform (mise, v1.17.2)
- [ ] Install the AWS Emulator (Docker route in [WORKFLOW.md](WORKFLOW.md))
- [ ] Run `aws login` (only needed from the staging stage on)
- [x] Create a Firebase project: `DinoGym`, project ID `dinogym-3870b`. The Android app is added to it later, once the package name and signing key exist.
- [x] Create a Grafana Cloud account (free tier). Tokens are created later, when observability is wired up.

## To verify once the emulator is installed

- [ ] Does our Terraform apply cleanly against the emulator? The docs say support varies by service.
- [ ] Do DynamoDB, SQS, S3 and EventBridge Scheduler behave as our code needs?
- [ ] Is "the emulator cannot run a Go Lambda" still true in the version we pin?
- [ ] Which emulator version do we pin?

## Still to decide

- [ ] The architecture write-up and the order we build in (the next step)
- [ ] The UI direction, then mock screens for approval (in progress in the Design thread). Style chosen (decisions 21 and 25); next: a font and colour test page, then `DESIGN.md`, then mocks
- [ ] The sync design: how conflicts between devices are resolved
- [ ] The DynamoDB data model
- [ ] The Android package name (it must differ from version 1's so both can be installed)
- [ ] The AWS region
- [ ] Where secrets live (session signing key, Grafana token). Likely SSM Parameter Store, which is not in STACK.md yet
- [ ] How the backend authenticates to FCM without a long-lived service account key (Google workload identity federation from AWS)

## To verify later

- [ ] The AWS free-tier terms for a new account (AWS changed them in 2025); [WISHLIST.md](WISHLIST.md) assumes the always-free allowances
- [ ] The foreground service type for the rest timer (`shortService` is capped at 3 minutes)
- [ ] The r/bodyweightfitness Recommended Routine wiki against the mirror used in [research/exercise-selection.md](research/exercise-selection.md)

- [ ] Google sign-in on the web dashboard accepts a `*.cloudfront.net` origin, since there is no custom domain

## Not started

- No code exists yet. The repository ([Pierorivera1/DinoGym](https://github.com/Pierorivera1/DinoGym)) holds only documentation. `docs/design/references/` (third-party art) is git-ignored and stays local.
