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

- **Decision:** Terraform on the laptop comes from mise (`mise use -g terraform`), not pacman. (Go also moved to mise, see decision 37.)
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

## 26. Training scope: Gym and Calisthenics (2026-11-16)

- **Decision:** Programs have a type, Gym or Calisthenics. Gym shows a weight field; Calisthenics hides it, with a per-exercise switch to turn it on (for example weighted dips).
- **Why:** The owner wants the app to serve people who train in a gym too, while he trains with calisthenics.
- **Rejected:** Keeping version 1's bodyweight-only scope.

## 27. Starter programs based on Jeff Nippard's free advice (2026-11-16)

- **Decision:** The app ships a Gym UL/PPL and a Calisthenics UL/PPL program, built from five routines (Upper, Lower, Push, Pull, Legs), plus a 4-day Upper/Lower layout for people with less time. The principles and exercise picks come from Nippard's free videos, blog and public program layouts, with sources in [research/](research/). The owner's own program ships as his first data. Details in [TRAINING.md](TRAINING.md).
- **Why:** The owner follows Nippard and wants evidence-based defaults. Using only free public content keeps the open-source repository clear of paid material.
- **Rejected:** Copying exercises or set tables from Nippard's paid programs; shipping no starter content; a large exercise library (the owner wants only what the programs use).
- **Honesty note:** No Nippard calisthenics video was found, so the calisthenics program applies his principles to bodyweight exercises; it is not presented as his exercise picks.

## 28. Progression: owner-driven, measured on set 1 (2026-11-16)

- **Decision:** Sets stay fixed. Gym exercises use double progression in 6–10 reps: add reps until 10, then add weight and restart at 6. Calisthenics adds reps when the user is ready. The app makes no progression suggestions; it pre-fills the last session's numbers. Progress counts only set 1, and the progress record keeps only the days set 1 beat the previous best. Weekly set volume is not tracked.
- **Why:** This is how the owner trains, and it matches Nippard's double progression. Set 1 is the freshest set, so it is the fairest comparison between sessions.
- **Rejected:** Automatic "try 13 next time" suggestions; tracking the best set of any position; weekly volume targets; built-in deloads.

## 29. Program cycle advances by completed workout (2026-11-16)

- **Decision:** A program is an ordered cycle of routines and rest days. The next routine is shown on the home screen; completing, marking a rest day done, or skipping moves the cycle on. It is not tied to weekdays.
- **Why:** The owner's 6-day cycle (Pull, Push, Legs, Rest, Upper, Lower) does not fit a 7-day week, and he sometimes skips Legs.
- **Rejected:** Weekday-based schedules.

## 30. Exercise icons, not photos (2026-11-16)

- **Decision:** Each exercise shows a small pixel icon for its type, drawn by us in the style of [design/PIXEL-STYLE.md](design/PIXEL-STYLE.md). No photos.
- **Why:** Matches the pixel-art style (decision 21), works offline, and avoids image licensing.
- **Rejected:** Photos from the public-domain free-exercise-db (wrong style, gaps for calisthenics skills); user-added photos (needs storage and permissions for little gain).

## 31. First daily-usable version (2026-11-16)

- **Decision:** The first version the owner switches to has: his program ready to log; adding and editing routines and programs; the rest timer with a scroll picker for minutes and seconds; editable history; the progress view; exercise icons. English and Spanish strings from the start. Then, in order: body weight, sign-in and sync, starter programs and a program builder, shareable routines, reminders, web dashboard.
- **Why:** These replace version 1 for daily use. The two version 1 complaints (hard to find how to add or edit a workout, no scroll picker for rest) are fixed in the first version. Cloud features come after the app is useful offline.
- **Rejected:** Building sign-in and sync first.

## 32. Body weight chart: 7-day average, inferred goal direction (2026-11-16)

- **Decision:** The body weight chart draws a 7-day trailing average as its main line with raw weigh-ins as small points. The goal direction (lose or gain) is inferred from the goal against the weigh-ins, with no separate setting.
- **Why:** Daily weight moves by about 0.5–1 kg from water and food, so the average shows the trend. Inferring the direction saves a setting. The owner left both choices to the AI.
- **Rejected:** Raw points only; a lose/gain/maintain setting.

## 33. Design canvas approved; DESIGN.md is the style guide (2026-11-16)

- **Decision:** The owner approved the style on the design canvas (style tile plus Home, active exercise, rest picker, rest timer and summary). Its values are in [DESIGN.md](DESIGN.md) and its source in [design/mocks/](design/mocks/). The owner left the open design questions to the AI, which settled them: the nav labels stay in Pixelify Sans at 11sp, auto-sizing down to 8sp with larger system fonts (the longest label, `EJERCICIOS`, measured 60.5dp against about 62dp of room on a 360dp phone); the START tab stays raised; the bottom bar is hidden during a workout.
- **Changes from PIXEL-STYLE.md:** the Night hard shadow is dark violet `#4A3A80`, because a white offset shadow on near-black was too loud; Day uses plum for secondary borders, since it has no orange token; rest days in the cycle strip show `ZZZ`, because `REST`/`DESCANSO` do not fit; the rest picker moves seconds in 5-second steps.
- **Why:** A tested set of values lets the screens be built without re-deciding the look.
- **Rejected:** Inter for the nav labels (not needed once measured); keeping the bottom bar during a workout (distracts from logging).

## 34. Android package name: io.github.pierorivera1.dinogym (2026-11-16)

- **Decision:** The application ID and namespace are `io.github.pierorivera1.dinogym`. Debug builds add `.debug`.
- **Why:** It must differ from version 1's `com.dinogym` so both install side by side. A package name should be a domain the owner controls; there is no custom domain (decision 23), but the GitHub account `Pierorivera1` is his, and `io.github.<user>` is the usual form for that.
- **Rejected:** `com.dinogym.v2` and `dev.pierorivera.dinogym`, which use domains he does not own. The application ID cannot change after release without losing updates, so it is fixed now.

## 35. One Gradle module for the app (2026-11-16)

- **Decision:** The app starts as a single `:app` module, with `domain/`, `data/`, `timer/` and `ui/` kept apart by package. See [ARCHITECTURE.md](ARCHITECTURE.md).
- **Why:** Simpler to build, explain and review. Fewer modules mean less Gradle configuration work, which matters with the CPU limits (decision 15). `domain/` stays free of Android imports, so it can be moved into its own module later if build time or reuse calls for it.
- **Rejected:** A multi-module layout (`core:*`, `feature:*`) as in Google's Now in Android sample. It pays off for large teams, not for one app of this size.

## 36. Room rows ready for sync from the start (2026-11-16)

- **Decision:** Every Room table uses a text UUID primary key, an `updatedAt` timestamp and a nullable `deletedAt` (soft delete).
- **Why:** Sync comes after the first version (decision 31), and its conflict design is still open. Whatever it becomes, rows created on two devices need IDs that do not collide, and deletions need to be sent to the server. Adding these columns now avoids a data migration later.
- **Rejected:** Auto-increment integer IDs and hard deletes, which would collide across devices and lose deletions.

## 37. Go installed through mise (2026-11-16)

- **Decision:** Go on the laptop comes from mise (1.27.4), like Terraform (decision 18).
- **Why:** That is how it is installed. One version manager for both tools, and versions can be pinned per project.
- **Rejected:** pacman, which was the earlier plan.

## 38. Scaffold versions: compileSdk 37, Room 3, JDK 21 for Gradle (2026-11-16)

- **Decision:** The version catalog pins the latest stable releases on 2026-11-16: Gradle 9.9.1, Android Gradle Plugin 9.5.1, Kotlin 2.4.21, KSP 2.3.14, Hilt 2.60.1, Navigation 3 1.2.1, Compose BOM 2026.09.00. Three choices go beyond "take the latest":
  - **compileSdk 37, targetSdk 36.** The current AndroidX libraries refuse to build against API 36. compileSdk only decides which APIs the code can see; targetSdk, which decides runtime behaviour, stays at 36 as planned.
  - **Room 3 (`androidx.room3` 3.0.3) with the bundled SQLite driver.** Room 3 is the current line: Kotlin-first, coroutine-only DAOs, generated with KSP. The bundled driver ships the same SQLite version on every phone instead of whatever the Android version has.
  - **Gradle runs on JDK 21**, named in `android/gradle/gradle-daemon-jvm.properties`. Gradle downloads it if it is missing, so the laptop (which has JDK 27 on the path), Android Studio and GitHub Actions all build with the same JDK.
- **Why:** A new project should not start on a line that is already superseded, and the build should not depend on which JDK a machine happens to have.
- **Rejected:** Room 2.8, which works but would need a migration to Room 3 later; holding AndroidX back to versions that accept compileSdk 36; relying on the JDK on the path (JDK 27 is newer than the Android tools are tested with).
- **Cost of the choice:** Room 3 is newer, so there are fewer examples online than for Room 2.

## 39. Theme and components: how the pixel look is built (2026-11-16)

- **Decision:** Task 3 built the theme in `ui/theme/` and the shared components in `ui/components/`. The choices behind it:
  - **Variable font files** for Pixelify Sans and Inter, with each weight pinned through `FontVariation`. Rejected: static files per weight, which need a font tool that is not installed. Cost: Inter adds about 860 KB to the APK.
  - **Pixel art is snapped to whole screen pixels**, so icons and the mascot stay sharp on densities such as 2.625x. Rejected: plain vector scaling, which blurs the edges.
  - **Our own border modifier** (`steppedBorder`) instead of Compose's `border()`, which fills in the cut corner pixel.
  - **The typewriter draws the untyped text transparent**, so the text box never changes size and screen readers get the whole sentence. It is off when the system has animations disabled.
  - **Home stays at the bottom of the back stack:** Back from any tab returns to Home, Back from Home leaves the app.
  - **Screenshots with Roborazzi and Robolectric** (test-only libraries, added to the version catalog). The laptop cannot run an emulator (decision 15), so a JVM test renders the components and screens to PNG files for comparing against the mocks. The images are written on every unit-test run and are not compared automatically, because font rendering differs between machines. Rejected: Google's Compose screenshot plugin (still alpha); no visual check at all.
- **Why:** These keep the look exact to DESIGN.md while staying inside the CPU limits.
- **Icon source:** `docs/design/icons/gen_icons.py` generates the vector drawables from the 16×16 bitmaps.

## 40. Database and training rules: details (2026-11-16)

- **Decision:** Task 4 built `domain/` (models and rules) and `data/` (Room, repositories, first-run seed). The choices behind it:
  - **The seed runs once, guarded by a row in a `local_flag` table** written in the same transaction as the seed. A crash halfway retries on the next launch; deleting everything later does not bring the program back. The table is local-only and never synced, so it has no UUID or timestamps. Rejected: a DataStore flag (two stores cannot be updated atomically); Room's `onCreate` callback (raw SQL only).
  - **`session.currentPosition`** holds which exercise a workout in progress is on, so a workout resumes after the app is killed and a skipped exercise stays skipped.
  - **Pre-fill reads the last session of the same routine; bests read every session of the exercise.** Dips have different targets in Push and Upper, so pre-fill is per routine, while a best is a best wherever it was done. With no history, pre-fill uses the routine target.
  - **The first session of an exercise is not tagged NEW BEST**, but it is the first entry of the progress list.
  - **Enums are stored as text names**, and unknown values fall back to a default. Rejected: type converters.
  - **Foreign keys use NO ACTION**, because rows are only soft-deleted (decision 36). Rejected: CASCADE, which would hard-delete history.
  - **Repositories are concrete classes without interfaces.** Tests use an in-memory database. Rejected: an interface per repository, which adds files and no behaviour.
  - **A cycle shortened below the current step restarts at step 0.** Rejected: clamping to the last step.
  - **At most one workout in progress:** starting a session while one is open returns the open one.
  - **Seed names are in the phone's language at first launch** (English or Spanish) and are user data afterwards. Rest defaults: 150 s for compound moves, 90 s for curls and raises (TRAINING.md).
- **Not modelled:** "each leg" is not stored; unilateral work is logged as one number, as the owner does (WISHLIST.md).
- **CI:** DAO and repository tests run on an emulator in GitHub Actions (API 34), never on the laptop.

## 41. Second mock batch, built before the owner's review (2026-11-16)

- **Decision:** The mocks for Programs, Exercises, Stats and Settings were added to the design canvas and `docs/design/mocks/`, and tasks 7–10 are built from them without waiting for the owner's approval. This is a one-time exception to decision 16: the owner asked for tasks 1–10 in one run and told the AI to make the open decisions. DESIGN.md marks these mocks "awaiting the owner's review"; anything the owner changes later is a follow-up task.
- **Interaction choices in the mocks:**
  - **No Save buttons.** Edits are stored as they happen, like logged sets. Removing an item from a list offers UNDO; deleting a whole program, routine or session asks first. Rejected: Save/Cancel on every editor, which needs "discard changes?" dialogs.
  - **Adding is always `+ NEW` in the section header**, and it opens the editor directly. This answers the version 1 complaint that add and edit were hard to find.
  - **Routines have their own list**, because one routine can be used by several programs.
  - **Exercises are archived, never deleted**, so their history and progress stay.
  - **Settings opens from a gear button on Home's header.** This changes the approved Home mock. Rejected: a sixth tab in the bottom bar.
  - **The bottom bar shows only on the four tab screens.** Editors and detail screens have a top bar with back.
- **Why:** Building from mocks keeps the screens consistent with the approved style even when the owner has not seen each one.
