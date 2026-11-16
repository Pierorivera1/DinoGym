# Open items

As of 2026-11-16. Remove an item when it is done or decided, and record decisions in [DECISIONS.md](DECISIONS.md).

## The owner will set up

- [x] Install Go (mise, 1.27.4, decision 37)
- [x] Install Terraform (mise, v1.17.2)
- [ ] Install the AWS Emulator (Docker route in [WORKFLOW.md](WORKFLOW.md)). Docker is installed but the service is stopped and the user is not in the `docker` group; this needs `sudo` once. Not needed until the cloud work
- [ ] Run `aws login` (only needed from the staging stage on)
- [x] Create a Firebase project: `DinoGym`, project ID `dinogym-3870b`. The Android app is added to it later, once the package name and signing key exist.
- [x] Create a Grafana Cloud account (free tier). Tokens are created later, when observability is wired up.

## To verify once the emulator is installed

- [ ] Does our Terraform apply cleanly against the emulator? The docs say support varies by service.
- [ ] Do DynamoDB, SQS, S3 and EventBridge Scheduler behave as our code needs?
- [ ] Is "the emulator cannot run a Go Lambda" still true in the version we pin?
- [ ] Which emulator version do we pin?

## Still to decide

- [ ] The owner reviews the second mock batch (Programs, Exercises, Stats, Settings): they are on the canvas and were built from without approval (decision 41)
- [ ] The sync design: how conflicts between devices are resolved
- [ ] The DynamoDB data model
- [ ] The AWS region
- [ ] Where secrets live (session signing key, Grafana token). Likely SSM Parameter Store, which is not in STACK.md yet
- [ ] How the backend authenticates to FCM without a long-lived service account key (Google workload identity federation from AWS)

## To verify later

- [ ] On a phone: Back handling in a workout, the notification permission prompt, the rest alarm and its sound with the screen off, resume after the app is killed, the scroll picker. None of these can be checked on the laptop
- [ ] With the system font at 130%, long button labels are cut off (`EMPEZAR PULL` shows as `EMPEZAR`)
- [ ] Exercise icons: only the pull-up icon is drawn; other exercises show a placeholder until task 8

- [ ] The AWS free-tier terms for a new account (AWS changed them in 2025); [WISHLIST.md](WISHLIST.md) assumes the always-free allowances
- [ ] The r/bodyweightfitness Recommended Routine wiki against the mirror used in [research/exercise-selection.md](research/exercise-selection.md)

- [ ] Google sign-in on the web dashboard accepts a `*.cloudfront.net` origin, since there is no custom domain

## State of the build

- Task 1 (project setup) is done: `android/` builds an empty app with Compose, Navigation 3, Hilt and Room wired in. Progress is tracked in [TASKS.md](TASKS.md). `docs/design/references/` (third-party art) is git-ignored and stays local.
