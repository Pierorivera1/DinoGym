# Decisions

Every decision made so far, in the order it was made. All were made on 2026-11-15, during planning. Add new ones at the end with a date.

## 1. The AI builds, the owner reviews

- **Decision:** The AI writes the project. The owner reviews small pull requests and each choice is recorded here.
- **Why:** The owner's hand-built learning projects are separate. This one must be resume-quality, and the owner must still be able to defend it in interviews.

## 2. A real app people use, not an infrastructure demo

- **Decision:** Build a product the owner and others use daily.
- **Rejected:** An uptime monitor, a code runner, a mini deploy platform. They show infrastructure well but are not something the owner would use.

## 3. Native Android, not a PWA

- **Decision:** A native Android app with a cloud backend behind it.
- **Why:** For an Android internship a PWA shows none of what is screened for (Kotlin, Compose, lifecycle, offline storage). A native app plus a backend serves both the mobile and the cloud reviewer.
- **Rejected:** A PWA, first suggested because a link is easy for recruiters to open.

## 4. Rebuild DinoGym

- **Decision:** Rebuild DinoGym from scratch instead of starting a new app or extending version 1.
- **Why:** The app and its first user already exist, and adding sync to an offline-first app is a hard, interview-worthy mobile problem. The owner dislikes version 1's UI and overall structure, so it is a rebuild, not an extension.
- **Rejected:** A job application tracker, a price tracker for Peruvian stores, a study app with shared decks.

## 5. Go for the backend

- **Decision:** Go.
- **Why:** The budget forces serverless, where startup time is what users feel. Go starts in about a tenth of a second on Lambda with a small memory footprint; a JVM often takes one to several seconds. Go is also common in cloud/DevOps roles.
- **Rejected:** Kotlin (Ktor), first recommended for sharing models with the app. Dropped once the budget was known. Python and TypeScript were also offered.
- **Cost of the choice:** No code shared with the app. An OpenAPI contract that generates the Kotlin client replaces it.

## 6. Under $10 a month, always on

- **Decision:** AWS spend stays under $10 a month and the backend is always reachable.
- **Consequence:** Serverless: Lambda, API Gateway, DynamoDB, SQS, EventBridge Scheduler, S3, CloudFront. No load balancer, no NAT gateway, no RDS.
- **Rejected:** About $25 on one small server; about $50+ for a load-balanced Fargate and Postgres layout; a demo-only stack created and destroyed on demand.

## 7. Features

- **Decision:** Accounts and multi-device sync, shareable routines by public link, push reminders, a web dashboard.
- **Rejected:** Friends and an activity feed.

## 8. Distribution through GitHub Releases

- **Decision:** A signed release APK published on GitHub Releases.
- **Rejected for now:** The Play Store ($25 once, and new personal accounts must run a closed test with 12 testers for 14 days before going public).

## 9. Observability: OpenTelemetry and Grafana Cloud

- **Decision:** Instrument the Go code with OpenTelemetry and send traces, metrics and logs to Grafana Cloud's free tier. Dashboards and alerts are defined in Terraform. Firebase Crashlytics covers app crashes.
- **Why:** It is the most widely asked-for observability stack and is free at this size.
- **Rejected:** AWS-native (CloudWatch and X-Ray); a self-hosted Prometheus, Loki, Tempo and Grafana stack.

## 10. Google sign-in only

- **Decision:** Sign in with Google through Android Credential Manager. The backend verifies the Google token and issues its own sessions.
- **Why:** No passwords to store or reset. Almost every Android user has a Google account.
- **Rejected:** Google plus email and password; email and password only.

## 11. One new repository

- **Decision:** A new single repository holding the Android app, backend, web and Terraform. The current DinoGym repository stays untouched as version 1.
- **Rejected:** Reusing the existing repository; separate repositories per part.

## 12. English and Spanish

- **Decision:** The app follows the phone's language, with English and Spanish.

## 13. No Docker for deployment

- **Decision:** The Lambda ships as a zipped Go binary. No container images and no registry.
- **Why:** The owner wants Docker only where it is needed. With Go on Lambda it is not.
- **Where Docker remains:** Running the AWS Emulator locally on Arch.

## 14. Emulator-first workflow

- **Decision:** Use the new AWS Emulator as the first gate: laptop, then pull request, then staging on real AWS, then prod. Details in [WORKFLOW.md](WORKFLOW.md).
- **Why:** Catch wrong AWS usage before anything costs money, keep control of the budget, and have a current, honest story for recruiters and LinkedIn.
- **Accepted limit:** The emulator cannot run the Go Lambda, route API Gateway or SQS to it, or check IAM. So the whole app is not "deployed" to the emulator, and a real staging stage is still required.
- **What we do not claim:** That the emulator proves reliability.

## 15. Local CPU limits

- **Decision:** Gradle capped at 3 workers with parallel builds off and a 2 GB heap; only the tests for changed code run locally; no Android emulator or instrumented tests locally; heavy suites run in GitHub Actions.
- **Why:** During version 1 an agent's builds or tests held the CPU at 100% and reached 90°C.

## 16. Design approach

- **Decision:** The AI proposes mock screens and the owner approves them before each screen is built.
- **Note:** The owner asked for a `/design` skill. None was available in the planning session, so the design work is done directly.

## 17. Keep version 1 data

- **Decision:** The new app imports the owner's existing DinoGym data (`workouts.json`).
- **Status:** Reversed on 2026-11-16 by decision 19.

## 18. Terraform installed through mise

- **Decision:** Terraform on the laptop comes from mise (`mise use -g terraform`), not pacman. Go stays on pacman.
- **Why:** Arch's package lagged behind (1.15.9 against 1.17.2), and mise can pin an exact version per project, so the laptop and GitHub Actions can run the same Terraform.
- **Rejected:** pacman, because it follows Arch's release pace; downloading the binary by hand, because nothing would update it.
- **Later:** The Terraform code will set `required_version` so a mismatched version fails early.

## 19. No import of version 1 data (2026-11-16)

- **Decision:** The new app starts with fresh data and does not read version 1's `workouts.json`. This replaces decision 17.
- **Why:** The owner does not need the old history. Dropping the import removes a one-off migration feature from the scope.
- **Rejected:** Importing version 1 data (decision 17).

## 20. Repository: DinoGym, public, open source (2026-11-16)

- **Decision:** The new repository is `Pierorivera1/DinoGym`, public and open source. Version 1 was renamed to `Pierorivera1/Old-DinoGym` so the new project can take the name.
- **Why:** The product keeps its name. A public repository gets free GitHub Actions minutes and lets reviewers read the code. Renaming keeps version 1's history visible instead of deleting it.
- **Rejected:** A new name for the rebuild; a private repository.
- **Note:** GitHub redirects the old URL to `Old-DinoGym` only until a new repository takes the `DinoGym` name. After that, old links lead to the new project.
- **License:** MIT (decision 24).


## 21. Visual style: pixel art, readability first (2026-11-16)

- **Decision:** The app and web pages use a pixel-art style inspired by Undertale / Deltarune (dark theme) and pastel pixel-art game UI (light theme). The style is extracted into [design/PIXEL-STYLE.md](design/PIXEL-STYLE.md). No art, fonts or characters from the references are used; all sprites and icons are our own, fonts are open-licensed, and the dino is the mascot.
- **Why:** The owner wants the app to feel like a game he enjoys while staying easy to read and use mid-workout. A distinctive look also makes the app memorable in a portfolio.
- **Rejected:** Stock Material 3 (looks like every other app, close to version 1); a full game-like UI with game layouts and menus (slower to use, and too close to copying a specific game).
- **Still open:** The questions listed at the end of PIXEL-STYLE.md; the final tokens go into `docs/DESIGN.md` once approved.

## 22. Lambda logs: short retention in CloudWatch, detail in Grafana (2026-11-16)

- **Decision:** Terraform creates each Lambda function's CloudWatch log group with 7-day retention. Functions use Lambda's JSON log format, with the application log level at WARN in prod and DEBUG in staging. Traces, metrics and detailed logs go to Grafana Cloud through OpenTelemetry (decision 9).
- **Why:** Lambda always writes its output to CloudWatch Logs, and a log group it creates by itself keeps logs forever, so cost slowly grows. Short retention and a quiet log level keep it near $0, while CloudWatch still holds what OpenTelemetry can miss, such as a crash before telemetry is sent.
- **Rejected:** Default settings (logs kept forever); blocking CloudWatch Logs completely by removing the write permission (no record of crashes); sending all logs to both places (duplicate cost).

## 23. No custom domain (2026-11-16)

- **Decision:** No custom domain for now. The API uses API Gateway's default URL and the web uses the CloudFront default domain (`*.cloudfront.net`).
- **Why:** Saves about $12 a year plus Route 53 costs, and removes DNS and certificate setup.
- **Rejected:** Buying a domain.
- **Consequence:** Shared routine links use the CloudFront address. Adding a domain later changes those links, so a later domain needs redirects from the old address.

## 24. MIT license (2026-11-16)

- **Decision:** The repository is released under the MIT license.
- **Why:** The shortest and most widely recognized permissive license. Anyone can read, reuse and learn from the code, which suits a public resume project.
- **Rejected:** Apache-2.0. Its explicit patent grant matters little for a personal app, and it is longer.


## 25. Style details: themes, fonts, navigation, mascot (2026-11-16)

- **Decision:** The dark theme follows Undertale, and the light theme follows pastel pixel art. The app has the game look only, with no streaks, levels or XP. Pixelify Sans is used only for short, big text (titles, buttons, navigation labels, big numbers), and Inter for everything people read. Both are OFL and bundled in the app and on the web. The bottom bar uses battle-style boxed buttons for Home, Programs, Start (centre, raised) and Stats, plus Exercises. The mascot, Dinocito, is a round yellow blob redrawn as a 32×32 sprite (`docs/design/mascot/`), not a dino. The Spanish STATS tab keeps the English label, and the chart ranges are `1M / 3M / 1A / TODO` in Spanish.
- **Why:** Piero's answers to the first style questions. Readability while training matters more than an all-pixel look. Game mechanics would add features without helping someone log a workout.
- **Rejected:** A pixel font for all text, which tires the eyes in long text. Streaks and `LV`/`HP` stats. Material Symbols mixed with pixel icons. A dino mascot.
- **Note:** Piero confirmed that the original mascot drawing can be used without permission.
