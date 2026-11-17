# Open items

As of 2026-11-17. Remove an item when it is done or decided, and record decisions in [DECISIONS.md](DECISIONS.md).

## The owner will set up

- [x] Install Go (mise, 1.27.4, decision 37)
- [x] Install Terraform (mise, v1.17.2)
- [ ] Run `aws login` (only needed from the staging stage on)
- [x] Create a Firebase project: `DinoGym`, project ID `dinogym-3870b`. The Android app is added to it later, once the package name and signing key exist.
- [x] Create a Grafana Cloud account (free tier). Tokens are created later, when observability is wired up.
- [ ] Back up the release signing key, `~/.config/dinogym/` (both files), to a password manager. Without it no update installs over the app ([RELEASE.md](RELEASE.md), decision 45)

## Still to decide

- [ ] The owner reviews the second mock batch (Programs, Exercises, Stats, Settings) and the body weight mock: they are on the canvas and were built from without approval (decisions 41 and 49). Also not drawn at all: the Stats PROGRESS list (decision 47) and the licences screen (decision 46)

## To verify later

- [ ] AWS provider 6.22.0 is pinned because of aws/aws-emulator#56 (decision 58). Move to the current provider once it is fixed
- [ ] Bump the pinned AWS Emulator version from time to time; security fixes only reach the latest release (decision 57)

- [ ] On a phone: Back handling in a workout, the notification permission prompt, the rest alarm and its sound with the screen off, resume after the app is killed, the scroll picker. None of these can be checked on the laptop
- [ ] On a phone, for the Exercises screens: drag to reorder, keyboard and focus, and that edits reach the database
- [ ] With the system font at 130%, long button labels are cut off (`EMPEZAR PULL` shows as `EMPEZAR`). No shorter Spanish verb exists, so this needs a layout fix
- [ ] On a phone, for tasks 7–11: drag to reorder in the cycle and routine editors, the exercise picker → exercise editor round trip, editing a past set, the theme switch, the kg keyboard on a weigh-in, Back closing sheets, TalkBack on the new screens
- [ ] When Night or Day is forced against the phone's dark mode, the window background shows the phone's mode for a moment at launch (decision 46)

- [ ] The AWS free-tier terms for a new account (AWS changed them in 2025); [WISHLIST.md](WISHLIST.md) assumes the always-free allowances
- [ ] The r/bodyweightfitness Recommended Routine wiki against the mirror used in [research/exercise-selection.md](research/exercise-selection.md)

- [ ] Google sign-in on the web dashboard accepts a `*.cloudfront.net` origin, since there is no custom domain

## State of the build

- Task 15: the API contract is `api/openapi.yaml`. It generates the Kotlin client (`android/api-client`) and the Go server interfaces (`backend/internal/api`); see decisions 60–62. Token formats for `/auth/*` are settled in task 20.
- Task 14: the AWS Emulator 1.0.491 runs in Docker; DynamoDB, SQS, S3, Scheduler and Terraform (provider 6.22.0) work on it; the Go Lambda cannot run on it ([WORKFLOW.md](WORKFLOW.md)).
- Task 12 decided the cloud items: sync (51), DynamoDB model (52), region (53), secrets (54), FCM auth (55). Still open in sync: concurrent pushes taking change numbers out of order (decision 51, task 21).
- Part 1 (tasks 1–10) and Part 2 (task 11) are done; progress is tracked in [TASKS.md](TASKS.md). The first release is tagged from `main` as described in [RELEASE.md](RELEASE.md). `docs/design/references/` (third-party art) is git-ignored and stays local.
