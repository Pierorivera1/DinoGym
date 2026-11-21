# Open items

As of 2026-11-20. Remove an item when it is done or decided, and record decisions in [DECISIONS.md](DECISIONS.md).

## The owner will set up

- [x] Install Go (mise, 1.27.4, decision 37)
- [x] Install Terraform (mise, v1.17.2)
- [ ] Before 2027-03-18 the AWS free plan ends: either the AWS side is torn down by then (planned for 2027-02-01, decision 76) or the account is upgraded to a paid plan
- [ ] Before task 26 reads it: create `/dinogym-<env>/grafana-token` with `aws ssm put-parameter --type SecureString` (decision 69). The session signing keys for staging and prod exist (task 20)
- [x] Create a Firebase project: `DinoGym`, project ID `dinogym-3870b`. The Android app is added to it later, once the package name and signing key exist.
- [x] Create a Grafana Cloud account (free tier). Tokens are created later, when observability is wired up.
- [ ] Back up the release signing key, `~/.config/dinogym/` (both files), to a password manager. Without it no update installs over the app ([RELEASE.md](RELEASE.md), decision 45)

## Task 25: to do and to verify

- [x] The owner reviews the dashboard mocks (approved 2026-11-20) (`WebHistory`, `WebProgress`, `WebWeight`, `WebPhone`, `WebSignIn`) before the web app is built (decisions 16 and 110)
- [x] Before building: add the dashboard's CloudFront origins (staging and prod) and `http://localhost`, `http://localhost:5173` as authorized JavaScript origins on the Web OAuth client (added 2026-11-20; Google accepted the `*.cloudfront.net` origins)
- [x] Decide how the browser keeps the session: in memory only (decision 111)
- [x] The owner signs in at `npm run dev` (web/dashboard/README.md) and sees the counts of workouts and weigh-ins: proves Google sign-in, `/auth/google` and the pull from a browser (2026-11-21: signed in against staging; 0 workouts and 0 weigh-ins, since staging has no synced data from the owner's phone)
- [x] Pull request 2: the views from the approved mocks (decision 112)
- [ ] The owner reviews the views on a phone and a desktop with real data (`npm run dev` against staging after syncing a debug build, or `/?demo` for made-up data)
- [ ] Pull request 3: CloudFront behaviours for `/auth/*` and `/sync/*`, the dashboard's routes, the upload in `deploy.yml`, then staging and prod

## Task 24: to do and to verify

- [x] The owner reviews `Reminders.dc.html` before the REMINDERS section is built (approved 2026-11-19)
- [x] The owner installs the Google Cloud CLI (AUR `google-cloud-cli` 594.0.0, 2026-11-18)
- [x] Before the backend PR merges: the owner applies `infra/bootstrap` (new boundary and deploy-role permissions, decision 108) and `infra/google` ([infra/README.md](../infra/README.md)). The project number (854486518090) is already in `envs/staging` and `envs/prod`
- [x] The owner registers the Android app (`io.github.pierorivera1.dinogym` and `.debug`) in the Firebase project `dinogym-3870b`
- [x] Before the next release (set 2026-11-20): the owner adds the GitHub secret `DINOGYM_FIREBASE_API_KEY` (the `current_key` in `google-services.json`); the release workflow fails without it (decision 109)
- [ ] Optional: restrict the Firebase API key in Google Cloud › Credentials to the two Android apps (package and signing SHA-1) and the Firebase Installations and FCM Registration APIs
- [x] The Android part: REMINDERS in Settings, the four preferences, FCM token registration, notification channels and the messaging service (decision 109)
- [x] On staging with a debug build on a phone (checked by the owner 2026-11-20): turn Workout day on for a few minutes ahead; the schedule fires, the federation token exchange works and the notification shows. Then the weekly summary (Sundays 20:00), the time sheet, ALLOW NOTIFICATIONS and sign-out (DELETE)

## Task 23: to do and to verify

- [ ] On a phone: App Links verify (`adb shell pm get-app-links io.github.pierorivera1.dinogym.debug` shows `d1q286cdeyw8ks.cloudfront.net: verified`)
- [ ] On staging: open a link in a desktop and a phone browser (Night and Day, English and Spanish); `assetlinks.json` is served as JSON; a share is cached by CloudFront (`x-cache: Hit from cloudfront` on the second request)
- [ ] On a phone: SHARE ROUTINE signed in and signed out, the share sheet, opening a link from a chat app, OPEN IN DINOGYM on the web page with and without the app, pasting a link, importing offline, and that imported rows sync
- [ ] The owner reviews `SharePage.dc.html`, `ShareFlow.dc.html` and `ImportShare.dc.html` (built before review, as in decision 41)
- [ ] Shares cannot be deleted yet (decision 97); add a "my links" list with delete if anyone asks

## Still to decide

- [ ] The owner reviews the new-program sheet mock (`NewProgram.dc.html`, task 22), built before review (decision 92)
- [ ] The owner reviews the second mock batch (Programs, Exercises, Stats, Settings) and the body weight mock: they are on the canvas and were built from without approval (decisions 41 and 49). Also not drawn at all: the Stats PROGRESS list (decision 47) and the licences screen (decision 46)

## To verify later

- [ ] First CI runs: the emulator image cache in `infra.yml` saves time; if not, remove it (decision 68)
- [ ] AWS provider 6.22.0 is pinned because of aws/aws-emulator#56 (decision 58). Move to the current provider once it is fixed
- [ ] Bump the pinned AWS Emulator version from time to time; security fixes only reach the latest release (decision 57)
- [ ] On staging: a full 500-row push finishes well inside the Lambda's 10 s timeout, and a 3,000-row first upload is not throttled (10 requests a second, decision 67). The emulator cannot show either (decisions 86–87)

- [ ] A second phone or a reinstall that signs in gets two copies of the seeded program, since every install seeds it with new ids (decision 90). Delete one by hand, or give seed rows fixed ids
- [ ] On a phone: Back handling in a workout, the notification permission prompt, the rest alarm and its sound with the screen off, resume after the app is killed, the scroll picker. None of these can be checked on the laptop
- [ ] On a phone, for the Exercises screens: drag to reorder, keyboard and focus, and that edits reach the database
- [ ] On a phone, for task 22: `+ NEW` → a starter → it opens in the program editor, Back closes the sheet, the new program syncs. In Spanish, the compact cycle strip cuts `TREN SUPERIOR` to `TREN SUPE` (also on program cards); shorter routine names or a two-line cell would fix it
- [ ] Starter exercises without a fitting icon use the generic one (deadlifts, leg curls, calf raises, flies, extensions); draw icons for them (decision 92)
- [ ] With the system font at 130%, long button labels are cut off (`EMPEZAR PULL` shows as `EMPEZAR`). No shorter Spanish verb exists, so this needs a layout fix
- [ ] On a phone, for tasks 7–11: drag to reorder in the cycle and routine editors, the exercise picker → exercise editor round trip, editing a past set, the theme switch, the kg keyboard on a weigh-in, Back closing sheets, TalkBack on the new screens
- [ ] When Night or Day is forced against the phone's dark mode, the window background shows the phone's mode for a moment at launch (decision 46)

- [ ] The AWS free-tier terms for a new account (AWS changed them in 2025); [WISHLIST.md](WISHLIST.md) assumes the always-free allowances
- [ ] The r/bodyweightfitness Recommended Routine wiki against the mirror used in [research/exercise-selection.md](research/exercise-selection.md)


## State of the build

- Task 24: the backend stores device tokens (`PUT`/`DELETE /devices/{installationId}`) and each reminder preference as an EventBridge Scheduler schedule; a separate reminder Lambda reaches FCM through workload identity federation (decisions 104–108). The app registers its FCM token, has REMINDERS in Settings and shows the workout-day and weekly-summary notifications (decision 109). Checked on a phone against staging by the owner (2026-11-20).
- Task 23: the API publishes and serves shares (`POST /shares`, public `GET /shares/{id}`, decisions 96–97); CloudFront serves the page `/r/<id>`, its files and `assetlinks.json` from a private bucket and sends `/shares/*` to the API (decisions 98–101); the app shares from the routine and program editors and imports through task 22's installer (decisions 102–103). Checked with Go unit tests, the emulator (including requests to the distribution it serves), JVM tests and screenshots; the installer's new test runs in CI. Staging and prod serve the page, `assetlinks.json` and shares through CloudFront (`d1q286cdeyw8ks` and `d1ktti3qymlgmy`.cloudfront.net, 2026-11-18); publishing and importing are not yet tried on a phone.
- Task 22: `+ NEW` on the Programs tab opens a sheet with an empty program and four starter programs (Gym and Calisthenics, 5 and 4 days, decision 92). Starters, the first-run seed and later imports are installed by `ProgramInstaller`, which reuses library exercises by name and kind (decision 93). Checked with JVM tests and screenshots; the installer's instrumented tests run in CI.
- Task 21: the backend's `/sync/push` and `/sync/pull` work on the laptop and the emulator (decisions 86–87). The app tracks changes with SQLite triggers, pushes and pulls through WorkManager while signed in, uploads the whole history on the first sign-in, and shows the sync row in Settings › ACCOUNT (decisions 88–91). Checked with JVM tests and CI's instrumented tests; not yet tried on a phone against staging.
- Task 20: the backend verifies Google ID tokens and issues sessions (decisions 79–80). The app signs in from Settings › ACCOUNT through Credential Manager, keeps the session encrypted with a Keystore key, and refreshes it under the rules of decision 83 (decisions 81–84). Debug builds use staging, release builds prod. Nothing else in the app needs an account.
- Task 19: the bootstrap is applied on real AWS (us-east-1) as the IAM user `piero-admin-dinogym`, its state is in the bucket, and the `staging` and `prod` GitHub environments and variables are set. Staging is deployed by `deploy.yml` and answers `/health`; prod deploys after the owner approves the run (decisions 74–78). The AWS side is torn down on 2027-02-01 (decision 76).
- Task 18: pull requests run the emulator checks (`infra/emulator/check.sh`); `deploy.yml` deploys staging, then prod after approval, through OIDC (decisions 68–73). `infra/bootstrap` holds the state bucket, the OIDC provider, the deploy roles, the permissions boundaries and the budget. The deploy jobs are skipped until `DEPLOY_ENABLED` is set in task 19, so neither workflow has reached AWS yet.
- Task 17: Terraform in `infra/` describes the backend for staging and prod: the table, the Go Lambda and the HTTP API (decisions 66–67; the SSM parameters were taken out in decision 69). It applies, plans clean and destroys on the emulator. Nothing exists on real AWS yet: the state bucket and the first apply are task 19, the deploy pipeline task 18.
- Task 16: the Go API (`backend/`) runs as one binary on the laptop and on Lambda, with OpenTelemetry (decisions 63–64). Its DynamoDB store applies the sync rules and is tested on the emulator, as are SQS, S3 and Scheduler (decision 65).
- Task 15: the API contract is `api/openapi.yaml`. It generates the Kotlin client (`android/api-client`) and the Go server interfaces (`backend/internal/api`); see decisions 60–62. Token formats for `/auth/*` are in decisions 79–80.
- Task 14: the AWS Emulator 1.0.491 runs in Docker; DynamoDB, SQS, S3, Scheduler and Terraform (provider 6.22.0) work on it; the Go Lambda cannot run on it ([WORKFLOW.md](WORKFLOW.md)).
- Task 12 decided the cloud items: sync (51), DynamoDB model (52), region (53), secrets (54), FCM auth (55). Ordering of concurrent pushes and the sync index lag were settled in task 21 (decision 86).
- Part 1 (tasks 1–10) and Part 2 (task 11) are done; progress is tracked in [TASKS.md](TASKS.md). The first release is tagged from `main` as described in [RELEASE.md](RELEASE.md). `docs/design/references/` (third-party art) is git-ignored and stays local.
