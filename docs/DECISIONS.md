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
- **Added 2026-11-17, why not Python or TypeScript:** both start about as fast as Go on Lambda, so speed was not the reason. Go was preferred for a single static binary with nothing to install or bundle, compile-time type checks, and its weight in DevOps tooling (Docker, Kubernetes and Terraform are written in Go). The known cost: the AWS Emulator runs only Python and Node.js Lambda code, so our Lambda wrapper cannot run locally (decision 14, [WORKFLOW.md](WORKFLOW.md)); the handlers and their AWS calls still can.

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
- **Corrected 2026-11-17 (task 14):** "cannot check IAM" is mostly true, but EventBridge Scheduler does check the role's identity policy when it delivers: a schedule whose role lacked `sqs:SendMessage` was dropped with `AccessDenied`. Most other calls are not checked. See [WORKFLOW.md](WORKFLOW.md).

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

## 42. Rest timer without a foreground service (2026-11-16)

- **Decision:** A rest is stored as an end time. While it runs, an ongoing silent notification shows a system countdown to that time. An exact alarm (`AlarmManager.setAlarmClock`) fires at the end and posts a "rest over" notification with sound and vibration, unless the app is on screen. The end time is saved in DataStore, so the rest survives the app being killed.
- **Why:** The timer must stay right with the screen off. A notification countdown and an alarm are handled by the system, so they keep working when the app's process is asleep or dead, and they use no battery in between. `setAlarmClock` is never delayed by Doze; the other exact alarm call is rate-limited to about one a minute, which back-to-back rests would hit. The cost is a small alarm icon in the status bar during a rest.
- **Permissions:** `POST_NOTIFICATIONS`; `USE_EXACT_ALARM` on Android 13 and later, `SCHEDULE_EXACT_ALARM` on Android 12. Google Play limits `USE_EXACT_ALARM` to alarm, timer and calendar apps; the app ships through GitHub Releases (decision 8), and this needs a second look if it ever goes to Play.
- **Rejected:** A foreground service. The `shortService` type stops after 3 minutes and rests go up to 9:00; the `specialUse` and `health` types need declarations or permissions the app has no other use for; and a service would still need an alarm or a wake lock to fire on time in Doze.
- **Not handled:** A reboot during a rest drops the alarm. A rest lasts at most 9 minutes, and the next app start clears it.

## 43. Workout flow behaviour (2026-11-16)

- **Decision:** Task 5 built Home, the active exercise, the rest picker, the rest timer and the summary from the approved mocks. Behaviour the mocks do not show:
  - **The workout moves to the next exercise by itself** after the last set of an exercise. The NEXT box at the bottom is information, not a button. Rejected: a tap on NEXT after every exercise.
  - **✕ and the system Back button ask first:** save and finish what was logged, discard, or keep going. Back on the rest screen returns to the exercise and the rest keeps running.
  - **A note is saved with the next set that is logged.**
  - **On launch, the app reopens a workout in progress**, and the rest screen if a rest is running.
  - **Sheets and confirmations are drawn inside the screen**, not as Material dialogs, so the screenshot tests can render them.
  - **Each screen is three parts:** a ViewModel with one state object, a stateless composable that draws that state, and a thin entry composable joining them. The stateless part is what the screenshot tests render.
  - **The notification permission is asked once**, the first time a rest starts (Android 13+). The timer works without it; only the notifications are missing.
- **Why:** Fewer taps between sets, and no way to lose a workout by accident.

## 44. Exercises screens and shared form components (2026-11-16)

- **Decision:** Task 8 built the Exercises tab (library and editor) and the form components the Programs, Stats and Settings screens will reuse (`ui/components/`: section header, top bar, text field, segmented control, switch, checkbox, tag, confirm sheet, undo bar, reorderable list). How editing works:
  - **Saving:** taps are stored at once; typing is stored 500 ms after the last key, and also when the screen is left or the app goes to the background, so the last keystroke is never lost. Rejected: a Save button (decision 41).
  - **Invalid input is never stored.** A blank name keeps the old name; a pattern without `{n}` keeps the last valid pattern and shows an error. Rejected: blocking the user from leaving the screen.
  - **Archiving stays on the editor**, and the button turns into RESTORE. Rejected: jumping back to the library.
  - **Drag to reorder is written by hand**, with Move up / Move down actions for TalkBack. It does not auto-scroll, so it suits short lists. Rejected: a reorder library, which would be a new dependency for one feature.
- **Icons:** every exercise type now has its own pixel icon, generated by `docs/design/icons/gen_icons.py`.

## 45. Release signing and the release workflow (2026-11-16)

- **Decision:** The release keystore was generated on the owner's laptop (`~/.config/dinogym/`, outside the repository) and copied into four GitHub repository secrets. Pushing a `v*` tag runs `.github/workflows/release.yml`, which checks the tag against `versionName`, builds `assembleRelease`, verifies the signature with `apksigner` and publishes the APK and its SHA-256 on GitHub Releases. Gradle reads the signing values from `DINOGYM_*` environment variables or a git-ignored `android/keystore.properties`; with neither, the release build is unsigned, so the push CI needs no secrets. How to release is in [RELEASE.md](RELEASE.md).
- **Why:** A tag is one deliberate step, and building in CI means every published APK comes from a commit on GitHub, not from a laptop. The secrets are only exposed to the job that runs on a tag the owner pushes.
- **Version:** `versionName` starts at 2.0.0, since this is the second DinoGym; `versionCode` starts at 1 and goes up by one per release, set by hand.
- **No minification yet:** R8 stays off. Shrinking could break Room, Hilt or serialization at runtime in ways only a phone shows, and the APK size does not matter for a sideloaded app. Rejected for now: R8 with keep rules, worth revisiting with tests on a device.
- **Rejected:** Signing on the laptop and uploading by hand (no record of what was built); Play App Signing (no Play Store, decision 8); a keystore committed in encrypted form (one leaked password away from public).
- **Risk:** The key cannot be replaced without uninstalling the app. The owner must back up `~/.config/dinogym/`.

## 46. Settings: theme storage and the licences screen (2026-11-16)

- **Decision:** The theme choice (System / Night / Day) is stored in its own Preferences DataStore file, `settings`, on the phone only. The activity draws nothing until it has been read, so the window background shows for those few milliseconds instead of a frame in the wrong theme. The open-source licences screen lists the bundled fonts (OFL 1.1, full text from `assets/licenses/`), the libraries in the APK (Apache 2.0, linked to the licence page) and SQLite (public domain, compiled into the APK by `sqlite-bundled`).
- **Why:** A theme belongs to a device, so it is not in Room, which is what syncs later (decision 36). Linking to the Apache 2.0 text avoids an asset per library.
- **Strings per feature:** new screens put their strings in their own file (`strings_settings.xml`), which keeps parallel work from conflicting in `strings.xml`.
- **Rejected:** Room for the theme (it would sync); `runBlocking` on the main thread to read the theme before the first frame; the AndroidX splash screen library or AppCompat just for this; the Google OSS licences plugin (a new dependency and Play-services tooling for one list).
- **Known gap:** When Night or Day is forced against the phone's dark mode, the window background matches the phone's mode until the first frame. Fixing that needs `UiModeManager.setApplicationNightMode` (Android 12+) and is left for later.
- **Mock:** The licences screen has no mock of its own; it reuses the Settings list style. It is a plain list, so it was built without one.

## 47. Stats screens: what counts as progress (2026-11-16)

- **Decision:** Task 9 built the Stats tab (HISTORY / PROGRESS), the past session and the progress view from the mocks. Behaviour the mocks do not show:
  - **A first ever set 1 is not a new best**, the same as the summary. Rejected: tagging first sessions.
  - **Weight shows only when set 1 logged more than 0 kg**, so bodyweight exercises in a Gym program never read `0 kg × 8`. Rejected: following the exercise's weight switch.
  - **GAIN and record deltas show kilograms when the weight went up** (`+2.5 kg`), otherwise reps, because more weight usually means fewer reps. Rejected: always reps.
  - **Chart:** the y axis is kilograms for weighted exercises (a same-weight record with more reps is a flat step; the list has the exact value) and reps otherwise. The x axis gives each month an equal slot from the first record to today, with at most 6 labels. Rejected: an estimated-strength scale; a plain linear date axis.
  - **Show-only exercises** (jump rope) are left out of PROGRESS: they never have results.
  - **Editing a past set saves on every tap**, one write at a time, in the app's scope so leaving the screen never drops the last tap. The summary recomputes when its session's sets change. Rejected: debouncing; a summary that shows stale numbers after EDIT.
  - **Deleting a session** removes it and any summary of it from the back stack. Rejected: returning to a summary of a deleted session.
- **Not drawn:** the PROGRESS list (exercise tile, name, best set 1) follows the history rows' style. It needs the owner's review with the rest of the second batch.
- **Why:** Records should mean real improvement, and a correction to a past set should show everywhere at once.

## 48. Programs screens: editing a cycle safely (2026-11-16)

- **Decision:** Task 7 built the Programs list, program editor, routine editor and exercise picker from the mocks. Behaviour the mocks do not show:
  - **Removing the step the cycle is on makes the next step current** (the first if it was the last), and UNDO restores it. Rejected: going back a step.
  - **UNDO clears `deletedAt` on the same row** instead of inserting a new one, so sync later sees one row, not a delete plus an insert.
  - **Deleting the active program leaves no program active**, and the dialog says so. Rejected: silently activating another one.
  - **A new program starts as Calisthenics** and becomes active only when no program is active, so a first program works on Home at once.
  - **Deleting a routine used by several programs** names them in the dialog, then removes its steps from every cycle. Rejected: blocking the delete. A routine with a workout in progress cannot be deleted; the sheet asks to finish or discard the workout first.
  - **Exercises added from the picker start at 2 × 10**, rest 1:30 for isolation (curls, raises) and 2:30 otherwise (TRAINING.md). Show-only exercises get 1 × 0 and no rest, as in the seed.
  - **Reps:** RANGE opens two reps above the fixed number and can never collapse to equal ends; sets 1–20, reps 1–999.
  - **The picker** searches ignoring case and accents, keeps ticked rows visible while searching, and allows adding an exercise that is already in the routine (some routines repeat one).
- **Not blocked:** editing a routine's exercises while a workout of it is open. The workout reads its exercises when it starts.
- **Why:** No edit can leave the cycle pointing at nothing, and every removal can be undone except whole-item deletes, which ask first (decision 41).

## 49. Body weight: storage, chart and entry (2026-11-16)

- **Decision:** Task 11 added body weight as the third Stats segment, WEIGHT (HISTORIAL / PROGRESO / PESO), with the mock `BodyWeight.dc.html` (built before the owner's review, as in decision 41). Two new tables move the database to version 2 with a written migration, tested in CI with Room's `MigrationTestHelper`:
  - `weigh_in` (time, kg), with the usual id, `updatedAt`, `deletedAt`.
  - `preference` (key, value, `updatedAt`, `deletedAt`): user settings that should sync later. The goal weight is the first, `body_weight_goal_kg`. The theme is not here, because it belongs to the device (decision 46).
- **Rules:**
  - Every weigh-in counts; several on one day are averaged into that day. The chart's main line is the 7-day trailing average (decision 32), with the raw weigh-ins as small points and the goal as a dashed line. Ranges: 1M, 3M, ALL.
  - CURRENT is the average ending on the latest day with a weigh-in, not today, so a gap never empties it.
  - A change toward the goal shows in `success`; a change away shows in plain text, never `danger`, because daily swings are normal.
  - The weight is typed (comma or point, 20–400 kg) with −/+ 0.1 kg nudges, on today or a past day. Rejected: a stepper alone (70 to 80 kg is 100 taps).
  - Deleting a weigh-in offers UNDO; clearing the goal soft-deletes its row.
- **Rejected:** A separate Body weight screen off Home (one more place to look); last-weigh-in-of-the-day only (throws data away); storing the goal in the device-only settings file (it would not sync); a destructive migration (the first release ships version 1 and its data must survive).
- **Known gap:** the sheet's scrim covers the Stats screen but not the bottom bar, which belongs to the app shell.

## 50. Spanish copy: Latin American, one term per concept (2026-11-16)

- **Decision:** Every Spanish string was reviewed before the first release. The app speaks Latin American Spanish with tú, as the mocks do, and uses one term per concept: entreno (workout), rutina, programa, serie, REPS, descanso, ejercicio, récord, peso, pesaje (weigh-in), objetivo (goal), promedio (average), agregar / quitar / borrar, empezar / seguir, ajustes, celular. Spain-only words were replaced (añadir → agregar, cuenta atrás → cuenta regresiva, media → promedio).
- **Why:** The owner and his likely users are Latin American, and one word per idea keeps the app predictable.
- **Rejected:** Neutral Spanish that avoids regional words (it reads stiff); mixing entreno, entrenamiento and sesión.
- **Left open:** `EMPEZAR %1$s` has no shorter natural verb, so the cut-off label at 130% font size needs a layout fix (OPEN-ITEMS).

## 51. Sync: newest edit per row wins, pulled by a server change number (2026-11-16)

- **Decision:** Devices sync whole rows. A device pushes the rows it changed; the server keeps a pushed row only if its `updatedAt` is newer than the stored one (a conditional write). A deletion is a row with `deletedAt` set, so it travels like any edit and wins the same way. Every row the server accepts gets the next number from a per-user counter. A device pulls "every row after number N" and stores the last number it saw as its cursor.
- **Details:**
  - `updatedAt` is written by the device. The server refuses a time more than a few minutes in its future, so one phone with a wrong clock cannot win every conflict forever.
  - On equal `updatedAt`, a deletion wins; otherwise the stored row stays.
  - Applying the same pulled row twice changes nothing, so retries are safe.
  - The rows already have the needed shape: UUID keys, `updatedAt`, soft delete (decision 36). `local_flag` and the theme never sync.
- **Why:** One user on a few personal devices rarely edits the same row on two of them before syncing, and logged sets are mostly only added. Whole-row last-writer-wins is simple to build, test and explain. Ordering the pull by a server number, not by timestamps, means a device never misses a row because of clock drift.
- **Rejected:** Field-by-field merging and CRDTs (far more work for conflicts that will almost never happen); pulling by `updatedAt` (device clocks drift, so rows would be skipped); asking the user to resolve conflicts (no one wants a merge dialog mid-workout).
- **Known risk, settled in task 21:** two pushes from the same user landing at once can take numbers 5 and 6 and commit 6 first, so a pull in between would skip 5. Task 21 decides the fix (handling one user's pushes one at a time, or re-reading a window behind the cursor) and tests it.
- **Settled 2026-11-17:** decision 86 (the pull cursor waits 30 seconds behind new rows).

## 52. DynamoDB: one table, keyed by user (2026-11-16)

- **Decision:** One on-demand table.
  - Synced rows: partition key `USER#<userId>`, sort key `<TYPE>#<uuid>` (for example `SESSION#…`, `SET#…`, `WEIGHIN#…`; preferences use `PREF#<key>`). The item holds the row's fields, `updatedAt`, `deletedAt` and its change number.
  - Per-user counter for change numbers (decision 51): `USER#<userId>` / `META`.
  - Sync index (GSI1): partition `USER#<userId>`, sort key the change number. A pull is one Query on it.
  - Google account to user: `GOOGLE#<sub>` / `USER`. Sessions and shared routines (`SHARE#<id>`) get their exact shape in tasks 20 and 23.
- **Why:** Every access is "this user's rows" or "this user's rows since N", which one table with one index answers. On-demand billing costs nothing when idle, which fits the budget (decision 6), and the always-free allowance covers this size.
- **Rejected:** A table per entity (more Terraform, no access pattern needs it); provisioned capacity (pays for idle throughput).

## 53. AWS region: us-east-1 (2026-11-16)

- **Decision:** Staging and prod run in `us-east-1` (N. Virginia).
- **Why:** The owner lives in Peru. Measured from the laptop (TCP connect to the DynamoDB endpoint, three tries each): `sa-east-1` about 90 ms, `us-east-1` about 97 ms, `us-east-2` about 110 ms. The difference to São Paulo is too small to feel in an app that syncs in the background, while São Paulo prices are higher and new services and features reach `us-east-1` first.
- **Rejected:** `sa-east-1` (São Paulo): closest, but costs more for no felt gain.

## 54. Secrets in SSM Parameter Store (2026-11-16)

- **Decision:** The backend's secrets (the session signing key, the Grafana Cloud token) are SecureString parameters in SSM Parameter Store, standard tier, encrypted with the AWS-managed `aws/ssm` key. The Lambda reads them at cold start. Terraform creates each parameter with a placeholder value and ignores later changes to it; the owner sets the real value once with the AWS CLI, so the secret never enters Terraform state or git.
- **Why:** Standard parameters are free, and the AWS-managed key avoids KMS charges. This adds a service that was not in STACK.md; it is now listed there.
- **Rejected:** Secrets Manager ($0.40 per secret per month; its automatic rotation is not needed here); Lambda environment variables set from Terraform (the value would sit in state and in the console); putting the value in Terraform (state would hold it).
- **Changed 2026-11-17 (decision 69):** Terraform no longer creates the parameters with a placeholder; the owner creates them.

## 55. FCM without a stored key: Google workload identity federation (2026-11-16)

- **Decision:** The reminder Lambda authenticates to Firebase Cloud Messaging through Google workload identity federation. A workload identity pool in the `dinogym-3870b` Google Cloud project trusts the Lambda's AWS role. The Lambda exchanges its own short-lived AWS credentials for a short-lived Google token that impersonates a service account allowed only to send FCM messages. No service-account key is ever created.
- **Why:** The project allows no long-lived keys anywhere (CLAUDE.md). A downloaded service-account JSON is exactly such a key, and it would need storing and rotating.
- **Rejected:** A service-account JSON stored in SSM.
- **Later:** setting up the pool needs the Terraform Google provider or `gcloud`, which is recorded when task 24 builds it.

## 56. Docker starts on demand; the user is in the `docker` group (2026-11-17)

- **Decision:** `docker.socket` is enabled instead of `docker.service`, so the Docker daemon starts the first time something calls it. The owner's user is in the `docker` group, so Docker, lazydocker and the agent run without sudo.
- **Why:** Docker only runs the AWS Emulator here (STACK.md). A daemon that is not running costs nothing on a laptop that runs hot, and the agent cannot type a sudo password for every command.
- **Rejected:** Starting `docker.service` at every boot (runs when not needed); rootless Docker (removes the root-equivalent risk of the `docker` group, but adds setup and differs from how CI runs Docker; acceptable to skip on a single-user laptop).
- **Known risk:** membership of the `docker` group is equivalent to root on this machine.

## 57. AWS Emulator: pinned, verified, run with limits (2026-11-17)

- **Decision:** Task 14 runs the AWS Emulator **1.0.491** (LTS) from our own image, `infra/emulator/Dockerfile`: Amazon Linux 2023 pinned by digest, and the RPM pinned by version and SHA-256 (`ADD --checksum`). Before pinning, the package's GPG signature was verified against the AWS key whose fingerprint the docs publish. `infra/emulator/start.sh` publishes the port on `127.0.0.1` only, caps memory at 2 GB with no swap, turns telemetry off and stops the emulator after an hour without an API call. `infra/emulator/env.sh` points one shell at it and unsets real AWS credentials. The results are in [WORKFLOW.md](WORKFLOW.md) and the report in [reports/aws-emulator/](reports/aws-emulator/).
- **Why:** The emulator is released several times a day (1.0.492 and 1.0.497 came out the same day as 1.0.491), so `LTS/latest` would change test results without any change of ours. Checksums make a changed download fail the build instead of being used. The run limits follow the laptop rules and keep it off the network.
- **Rejected:** Installing `LTS/latest` as the docs show; building from source (about 100 GB and a long compile); publishing the port on all interfaces.
- **Cost:** Security fixes only go into the latest release, so the pin must be bumped on purpose (steps in WORKFLOW.md). The image takes 2 GB of disk.

## 58. Terraform AWS provider pinned to 6.22.0 (2026-11-17)

- **Decision:** Our Terraform pins `hashicorp/aws` to exactly 6.22.0, for both the emulator and real AWS, so the same configuration is applied in every stage.
- **Why:** From 6.23.0 the provider reads S3 bucket tags through S3 Control, whose requests go to `<account-id>.127.0.0.1`; that name does not resolve, so every `aws_s3_bucket` fails on the emulator (aws/aws-emulator#56, open). Tested: 6.73.0 failed on the bucket; 6.22.0 applied all six probe resources, planned no changes afterwards, and destroyed cleanly.
- **Rejected:** The latest provider with S3 kept out of the emulator stage (two code paths, and S3 untested before staging); the latest provider with no Terraform apply on the emulator at all (loses the check that stage 2 exists for).
- **Revisit:** when #56 is fixed, move to the current provider. 6.22.0 is about a year old and gets no fixes.

## 59. Emulator checks become Go integration tests (2026-11-17)

- **Decision:** The manual checks from task 14 (the DynamoDB sync rules, SQS, S3, Scheduler delivery) become Go integration tests in task 16, run against the emulator in CI from task 18.
- **Why:** The checks then live next to the code they protect and use the same AWS SDK the backend uses. No new language or tool is added.
- **Rejected:** A Bash script with the AWS CLI (would duplicate what the Go tests check); a Python script with boto3 (adds a tool not in STACK.md for the same result).

## 60. One OpenAPI contract generates both the Kotlin client and the Go server (2026-11-17)

- **Decision:** `api/openapi.yaml` (OpenAPI 3.0.3) is the API's single source. The Kotlin client is generated by the OpenAPI Generator Gradle plugin 7.27.0 (`kotlin` generator, `jvm-ktor` library, kotlinx.serialization, Ktor 3.6.1) into a new plain-JVM module, `android/api-client`; the generated code stays in `build/` and is never committed. The Go server types and interfaces are generated by oapi-codegen v2.8.2 (strict server on `net/http`, spec embedded), pinned as a `tool` in `backend/go.mod`, into the committed `backend/internal/api/api.gen.go`. CI regenerates it and fails on any difference.
- **Details:**
  - The first version covers only what tasks 16–21 need: `GET /health`, `POST /auth/google`, `POST /auth/refresh`, `POST /sync/push`, `GET /sync/pull`. Later tasks add their endpoints here first.
  - The generated `ApiClient` installs JSON content negotiation with no format, so the hand-written `DinoGymApi` builds every generated class with kotlinx JSON, `ignoreUnknownKeys` (an old app survives a new response field) and `explicitNulls = false`.
  - `android/api-client` is a second Gradle module, an exception to decision 35: generated code is kept out of `:app`, and its tests run as plain JVM tests with Ktor's `MockEngine`.
- **Why:** With both sides generated, neither can drift from the contract, and a breaking change shows up as a compile error on the side that missed it. OpenAPI 3.0.3 because both generators support it fully; 3.1 support is still partial and nothing here needs it.
- **Rejected:** A hand-written Ktor client (the contract would only be documentation); generating only the client and hand-writing Go handlers (the server could drift unnoticed); the `multiplatform` Kotlin library (aimed at KMP projects; this app is Android only); committing the generated Kotlin (Gradle regenerates it from cached inputs, so it would only add noise to reviews).
- **Known limits:** The generated Kotlin is plain and verbose, so only the data layer should use it. An unknown enum value (for example a new `SyncTable` from a newer server) still fails to decode; the server must not send new enum values to old apps without a version check.

## 61. Lint the contract with Redocly CLI (2026-11-17)

- **Decision:** CI lints `api/openapi.yaml` with Redocly CLI 2.76.0 (`recommended-strict` rules, `api/redocly.yaml`), run through `npx` with the version pinned. The one rule turned off is "every operation has a 4XX response", since `/health` has none to give. The version is a release at least a week old, not the newest.
- **Why:** Generators accept specs that are vague or inconsistent (missing operation ids, unused schemas, undeclared errors); a linter catches these before they turn into odd generated code. Node is only needed in CI and for an optional local run, never by the app or backend.
- **Rejected:** Spectral (similar rules, needs custom rulesets for the same result); vacuum as a Go `tool` in `backend/go.mod` (keeps Node out but adds a large dependency tree to the backend module for a lint step).

## 62. Sync on the wire: rows grouped by table, a result per pushed row (2026-11-17)

- **Decision:** `POST /sync/push` and `GET /sync/pull` exchange a `ChangeSet`: one typed list per synced table (`programs`, `cycleSteps`, … `preferences`), each row exactly as in Room, deletions included with `deletedAt` set. Times are epoch milliseconds, as in Room. Push answers with one result per row: `accepted` with its change number, `stale` (the server has a row at least as new; it comes back on the next pull) or `rejected` with a reason (such as `updated_at_in_future`). A push is limited to 500 rows. Pull takes `since` and `limit` (default 500, at most 1000) and returns `cursor` and `hasMore`. The workout table is `WorkoutSession` on the wire so it is not confused with a sign-in `Session`.
- **Why:** A list per table is fully typed in both generators without polymorphism. `oneOf` with a discriminator is where the Kotlin generator is weakest. Per-row results let the device clear its pending flags exactly and tell "already newer on the server" apart from a refusal.
- **Rejected:** One list of rows with a `type` discriminator (`oneOf`), which generates poorly in Kotlin; a free-form `data` object per row (no types, no validation); ISO 8601 strings for times (the app and the change rules already use milliseconds, so they would be converted twice).
- **Still open:** the push response only reports change numbers; the device keeps its pull cursor from pulls alone. Ordering of concurrent pushes stays with task 21 (decision 51).
- **Settled 2026-11-17:** ordering of concurrent pushes in decision 86; the push and pull handlers in decision 87.

## 63. One Go binary for the laptop and Lambda, with our own small API Gateway adapter (2026-11-17)

- **Decision:** `backend/cmd/api` builds one binary. When the Lambda runtime is present (`AWS_LAMBDA_RUNTIME_API` is set) it starts the Lambda loop with `aws-lambda-go`; otherwise it serves HTTP on `127.0.0.1:8080`. Both paths run the same `http.Handler`. A package of about 120 lines, `internal/lambdahttp`, converts API Gateway HTTP API events (payload 2.0) to `http.Request` and back, including base64 bodies and cookies, and runs a hook after each invocation to flush telemetry. Endpoints whose task is not built yet answer 501 `not_implemented`; every error uses the contract's `Error` shape; request bodies are capped at 1 MiB.
- **Why:** The emulator cannot run Go Lambdas (decision 14), so the handlers must run as an ordinary program on the laptop, and keeping one code path means what is tested locally is what Lambda runs. The adapter is small enough to read in an interview and is unit-tested with sample events.
- **Rejected:** `awslabs/aws-lambda-go-api-proxy` (its module pulls in Gin, Echo, Fiber and others to adapt one `net/http` handler); `aws-lambda-go/lambdaurl` (built for Lambda function URLs with response streaming, which API Gateway does not support); the Lambda Web Adapter extension (a layer and a second process per instance, for what 120 lines do).
- **Cost:** The adapter must keep up with payload 2.0 by hand. It assumes the `$default` stage, so paths carry no stage prefix (task 17).

## 64. OpenTelemetry setup: OTLP/HTTP, flushed per invocation, JSON logs to stdout (2026-11-17)

- **Decision:** `internal/telemetry` sets up traces, metrics and logs with the OpenTelemetry Go SDK 1.47. `DINOGYM_TELEMETRY` picks the mode: `otlp` (the default when `OTEL_EXPORTER_OTLP_ENDPOINT` is set; the exporters read the standard `OTEL_EXPORTER_OTLP_*` variables, which is how Grafana Cloud is reached), `console` (spans printed to stdout, for the laptop) or `none`. HTTP requests are traced with `otelhttp`, with spans named by route (`GET /sync/pull`), never by raw URL. Logs go through `slog` as JSON to stdout, which Lambda sends to CloudWatch (decision 22), and also to OTLP in `otlp` mode. On Lambda, every invocation ends with a flush of all three signals.
- **Why:** Lambda freezes the process between invocations, so batched exports would sit unsent or be lost; flushing per invocation trades a few milliseconds per request for complete data. OTLP over HTTP is what Grafana Cloud accepts directly, with no collector to run.
- **Rejected:** `contrib/exporters/autoexport` (reads the same variables but also links the gRPC and Prometheus exporters); the ADOT Lambda layer with its collector (an extra layer and process, and more cold-start time); OTLP over gRPC (no gain for this traffic).
- **Known cost:** The OTLP exporters still link gRPC types for their error handling, so the stripped arm64 binary is about 18 MB. The Grafana token comes from SSM (decision 54) when observability is wired up in task 26; until then `OTEL_EXPORTER_OTLP_HEADERS` is unset and staging runs with `none`.

## 65. The DynamoDB store: one sync index on the partition key, the counter before the write, integration tests behind a build tag (2026-11-17)

- **Decision:** `backend/internal/store` applies decision 51 on the table from decision 52, with these details:
  - Items use `PK` (`USER#<id>`), `SK` (`<KIND>#<id>`), `updatedAt`, `deletedAt` (only when set) and `changeNumber`, plus the row's own fields as attributes. The sync index `GSI1` is keyed on `PK` and `changeNumber`, so no separate `GSI1PK` copy is stored, and items without `changeNumber` (the counter, and later accounts and sessions) stay out of the index.
  - Put bumps the user's counter (`META`, `ADD seq 1`) and then writes the row with the condition `attribute_not_exists(PK) OR updatedAt < :u`, plus `OR (updatedAt = :u AND attribute_not_exists(deletedAt))` for a deletion. A stale row leaves a gap in the numbers, which a pull ("after N") skips without harm.
  - A row more than 5 minutes ahead of the server clock is rejected before any write.
  - A pull is one Query on `GSI1` for one row more than the limit, which says whether more remain.
  - The AWS SDK for Go v2 makes the calls, and `otelaws` gives each call a span. On the laptop pointed at the emulator, the binary creates the table at start; staging and prod get it from Terraform (task 17), which must use the same key schema.
  - The emulator checks of decision 59 are Go tests behind the `integration` build tag: the sync rules on the real store code, plus SQS, S3 and Scheduler delivery to SQS. They refuse to run unless `AWS_ENDPOINT_URL` points at this machine, and they create uniquely named resources and delete them afterwards.
- **Why:** The fewest calls and attributes that do what decision 51 says. Allocating the number first keeps a push to two simple calls. The build tag keeps `go test ./...` fast and free of Docker, while the integration tests use the same SDK and code as the API.
- **Rejected:** A transaction that reads the counter and commits the row and the new count together (no gaps, and commits in number order, but more code, double write cost and retries; the index is eventually consistent anyway, so it would not settle decision 51's risk on its own, which stays with task 21); a separate `GSI1PK` attribute (a copy of `PK` with no access pattern needing it); a JSON blob for the row's fields (hides them from the console and from future filters); separate test directories or a `testing.Short` switch instead of a build tag (the tag keeps the AWS test code out of normal builds altogether).
- **Cost:** Found by the emulator tests: DynamoDB refuses expression names the expression does not use, and returns numbers normalized (`73.0` comes back as `73`). The AWS SDK adds about 6 MB to the Lambda binary (stripped arm64: about 24 MB, up from 18).
- **Changed 2026-11-17:** a push takes its change numbers as one block with a single counter update, and every row stores `acceptedAt` (decision 86).

## 66. Terraform layout: one module, one root per environment, state in S3 with its own lock file (2026-11-17)

- **Decision:** `infra/modules/dinogym` describes one environment's backend. `infra/envs/{emulator,staging,prod}` are thin roots that call it, each with its own state: the emulator root keeps local state (the emulator forgets everything when it stops anyway); staging and prod use the S3 backend with `use_lockfile = true` and `encrypt = true`, under the keys `staging/terraform.tfstate` and `prod/terraform.tfstate`. The bucket name is passed at `terraform init -backend-config="bucket=…"`, and the bucket itself is created once by hand in task 19, since Terraform cannot store its state in a bucket it has not created yet. `infra/scripts/build-lambda.sh` builds the arm64 binary and zips it reproducibly to `backend/bin/api.zip`, which the module reads with `filebase64sha256`, so an unchanged build plans no change. Terraform is pinned to exactly 1.17.2 (`required_version`, decision 18).
- **Why:** Separate roots make the target explicit: applying to prod means being in `envs/prod` with prod's state, which cannot happen by forgetting a `workspace select`. S3's native lock file (Terraform 1.10 and later) replaces the DynamoDB lock table, one fewer resource to bootstrap. Building the zip with a script keeps Terraform free of a second provider and lets CI build once and plan with the same file.
- **Rejected:** Terraform workspaces (one root, the environment picked by hidden CLI state; easier to apply to the wrong one); a DynamoDB lock table (deprecated in favour of `use_lockfile`); the `hashicorp/archive` provider to zip the binary (another provider to pin and download, for what one `zip` command does); Terragrunt (a second tool for three small roots).
- **Cost:** The three roots repeat about 30 lines of provider and backend setup. The binary embeds the git commit (`main.version`), so every new commit redeploys the Lambda even when the Go code did not change.

## 67. Backend resources: what the code uses now, with a request cap (2026-11-17)

- **Decision:** Task 17 creates only what the API uses today: the DynamoDB table (`dinogym-<env>`, keys as in `store.CreateTable`), the Lambda (`provided.al2023`, arm64, 256 MB, 10 s timeout, JSON log format, its 7-day log group from decision 22), an API Gateway HTTP API with a `$default` route and stage (decision 63), and the two SSM SecureString parameters from decision 54 with placeholder values. The Lambda's role may write its own logs, Get/Put/Update/Query its own table and index, and read those two parameters, nothing else. The stage throttles at 10 requests a second with bursts of 20. Prod's table has deletion protection and point-in-time recovery; staging's does not, so it can be torn down. SQS, Scheduler and the S3/CloudFront web hosting come with tasks 24 and 25.
- **Why:** Resources nobody calls are cost and attack surface with nothing to test them. The throttle is the cheapest guard on the $10 budget (decision 6): a client stuck in a retry loop gets 429s instead of a bill. 256 MB gives the Go binary more CPU for a faster cold start, and stays well inside the free tier at this traffic. Point-in-time recovery costs cents for a table this small and protects the owner's real training history.
- **Rejected:** Creating SQS, Scheduler and the web bucket now (unused until their tasks); a Lambda function URL instead of API Gateway (no throttling, and decision 63's adapter is built for API Gateway); API Gateway access logs (CloudWatch ingestion cost; traces cover requests in task 26); 128 MB (slower cold start for no saving at this volume).
- **Changed 2026-11-17:** the SSM parameters were removed from Terraform (decision 69), and the API role gets a permissions boundary in staging and prod (decision 71).
- **Checked on the emulator:** apply, an empty second plan, drift on point-in-time recovery reverted, deletion protection blocking a delete, and destroy ([WORKFLOW.md](WORKFLOW.md)). The emulator cannot check IAM or run the Lambda, so the role and the API path are proven in staging (task 19).

## 68. Pull request checks: one script on the emulator, for CI and the laptop (2026-11-17)

- **Decision:** `infra/emulator/check.sh` runs the pull request stage: `terraform fmt -check`, `validate` of the staging and prod roots, the Go integration tests, then apply, `plan -detailed-exitcode` (anything but "no changes" fails) and destroy of `envs/emulator`. It sources `env.sh` in its own process and refuses to run if the emulator is not healthy; a trap destroys what it made if a later step fails. `.github/workflows/infra.yml` only prepares the runner (Go, Terraform 1.17.2, the emulator) and calls it. It runs on pull requests touching `api/`, `backend/`, `infra/` or itself, and `deploy.yml` calls it before every deploy. The built emulator image is cached with `docker save`/`load` keyed on the Dockerfile and `start.sh`; Terraform providers share one plugin cache across the three roots.
- **Why:** One script means what passes on the laptop is what CI checks. `main` has no branch protection, so a merge does not prove the checks passed; running them again in the deploy is what keeps unchecked code off AWS. The image is fully pinned (base by digest, package by SHA-256), so caching it is safe, and it skips a 340 MB download that would otherwise fail CI whenever that site is down.
- **Rejected:** Requiring the caller to source `env.sh` (easy to forget, and it leaves the shell pointed at the emulator); rebuilding the image on every run; running on every pull request without a path filter (Android-only changes would start the emulator); a required status check on `main` instead of rerunning in the deploy (a path-filtered check that never starts blocks the merge forever).
- **To verify:** the cache's real time saving on the first CI runs; the image is about 2 GB unpacked, so restoring it is not free.

## 69. SSM secrets are created by the owner, not Terraform (2026-11-17, corrects decision 54)

- **Decision:** Terraform no longer creates the secret parameters. The owner creates `/dinogym-<env>/session-signing-key` and `/dinogym-<env>/grafana-token` with `aws ssm put-parameter --type SecureString` before the code reads them (tasks 20 and 26). Terraform only grants the Lambda `ssm:GetParameters` on those two ARNs, built from their names. The deploy roles have no SSM permissions.
- **Why:** Found in review: the AWS provider reads a SecureString's decrypted value into state on every refresh, and `ignore_changes` does not stop that. The real secret would have reached state as soon as the owner set it, which defeats decision 54.
- **Rejected:** Keeping the placeholder resource (secret in state); the resource's write-only value argument (its read-back behaviour on provider 6.22.0 was not verified, and keeping values out of Terraform entirely needs no proof); a data source (also stores the value).
- **Cost:** A manual step outside Terraform. A missing parameter shows up when the Lambda starts, not at plan time.

## 70. Bootstrap stack and deploy roles: OIDC, one role per GitHub environment (2026-11-17)

- **Decision:** `infra/bootstrap` is a Terraform root the owner applies once by hand (task 19). It creates:
  - The state bucket `dinogym-tfstate-<account-id>`: versioned (old versions expire after 90 days), SSE-S3, public access blocked, non-TLS requests denied, `force_destroy` off. The bootstrap starts on local state through a git-ignored `local_override.tf`, then moves itself into that bucket with `terraform init -migrate-state` (key `bootstrap/terraform.tfstate`).
  - The GitHub OIDC provider, and the roles `dinogym-deploy-staging` and `dinogym-deploy-prod`. Each trusts only `sub = repo:Pierorivera1/DinoGym:environment:<env>` with `aud = sts.amazonaws.com`, for one-hour sessions.
  - Each role's inline policy: what Terraform needs for the module, scoped by name (`dinogym-<env>*`, `/aws/lambda/dinogym-<env>-*`), only its own state key and `.tflock`, and `iam:PassRole` only on `dinogym-<env>-api` and only to Lambda. Exceptions, commented in the policy: API Gateway (its ARNs hold random IDs, so access covers every HTTP API in the account) and two list calls that need `*`.
- **Why:** The GitHub environment carries the approval gate and the main-only rule, so trusting the environment (not the branch) makes those rules the boundary of AWS access. No state is left only on the laptop, which matters because the bootstrap state holds the budget email.
- **Rejected:** Trusting `ref:refs/heads/main` (prod would not need approval); one role for both environments; AdministratorAccess or PowerUserAccess (too broad to defend); keeping the bootstrap state on the laptop for good; creating the bucket with the CLI or CloudFormation (not in code, or a second tool); SSE-KMS (cost and a key to manage).
- **Proven on the emulator:** apply, an empty second plan, the state migration into the bucket and back, and destroy (refused while the bucket held state versions, as intended). The emulator sets `blocked_encryption_types = ["SSE-C"]` on new buckets like S3 now does, so the config states it to keep plans empty. It does not evaluate IAM, so the roles' permissions are first tested by the first staging deploy.

## 71. Permissions boundary on roles the deploy roles create (2026-11-17)

- **Decision:** The bootstrap creates a managed policy `dinogym-<env>-boundary` per environment allowing only what the API needs: write its logs, Get/Put/Update/Query its table and index, read its two SSM parameters. `envs/staging` and `envs/prod` attach it to the API role through the module's `permissions_boundary` variable (`envs/emulator` passes none: it has no bootstrap). A deploy role may create a role, put or attach a policy, or set a boundary only when `iam:PermissionsBoundary` equals its environment's boundary. Explicit denies stop it from removing any boundary and from touching the boundary policies or the `dinogym-deploy-*` roles.
- **Why:** A deploy role must create the Lambda's role and write its policy. Without a boundary, a compromised `main` could write an admin policy onto a role it made, have that role trust itself, and take over the account. A role's effective permissions are the overlap of its policies and its boundary, so nothing a deploy role creates can exceed the API's needs. Explicit denies win over any allow, including ones added by mistake later.
- **Rejected:** Limiting IAM writes to `role/dinogym-<env>-api` alone (its policy and trust can still be rewritten); having the bootstrap own the Lambda role so CI has no IAM write at all (safer, but every new role, such as Scheduler's in task 24, would need a manual bootstrap apply, and the module would stop describing its environment fully); documenting the risk only.
- **Cost:** The boundary must be kept in step with the API role's policy: a permission added to the role but not the boundary silently does nothing. Changing the boundary needs a manual bootstrap apply.

## 72. Budget alert: $10 a month, by email (2026-11-17)

- **Decision:** An AWS Budgets monthly cost budget of $10, in the bootstrap, emailing at 50% and 100% of actual spend and at 100% forecast. The email is a required variable with no default, passed with `TF_VAR_budget_email`, never committed.
- **Why:** Decision 6's limit, enforced by a warning that arrives before the bill. The first two budgets in an account are free.
- **Rejected:** Cost Anomaly Detection alone (no fixed limit); an SNS topic (an extra resource for one recipient).
- **Limit:** A budget warns, it does not stop spending. The API throttle (decision 67) is what caps runaway traffic.

## 73. Deploy workflow: build once, plan to a file, staging then approved prod (2026-11-17)

- **Decision:** `.github/workflows/deploy.yml` runs on pushes to `main` touching `api/`, `backend/`, `infra/` or the workflows, and by hand. Jobs: `checks` (calls `infra.yml`, decision 68) → `build` (the zip once, kept as an artifact for 30 days to outlive the approval wait) → `staging` → `prod`. Each deploy job signs in through OIDC with its environment's `AWS_DEPLOY_ROLE_ARN`, runs `terraform plan -out` and applies that file, then calls `/health` up to 6 times, 10 s apart. Each environment has its own concurrency group with `cancel-in-progress: false`. Every job is skipped until the repository variable `DEPLOY_ENABLED` is `true` (task 19).
- **Why:** Prod runs exactly the binary that passed staging. Applying the saved plan means what is applied is what was printed. A cancelled apply leaves a held lock and half-made resources. A job's `if` cannot read environment variables (they load once the job starts in its environment), so a repository-level switch is what lets pushes skip cleanly before the AWS side exists.
- **Rejected:** Building per environment; `apply -auto-approve` without a saved plan; letting the jobs fail until task 19; a workflow_dispatch-only trigger.
- **Accepted gap:** Prod's plan runs only after approval, so the reviewer approves the commit, not the prod plan. Staging's plan from the same commit is the preview.

## 74. The laptop signs in to AWS as an IAM admin user with MFA, through `aws login` (2026-11-17)

- **Decision:** Root is used only for account settings (root MFA, IAM access to billing). Everything else, including the bootstrap apply, runs as the IAM user `piero-admin-dinogym` with `AdministratorAccess`, console password and MFA, and no access keys. The CLI signs in with `aws login --profile dinogym`, which uses the console sign-in and gives short-lived credentials.
- **Why:** Root cannot be limited by any policy and its credentials can close the account; an admin user with MFA can be restricted or deleted. `aws login` means no long-lived keys on the laptop (CLAUDE.md).
- **Rejected:** Root for the bootstrap (works, but the habit is the one to avoid); IAM Identity Center (the standard for many accounts and people, but for one person and one account it adds an organization and a portal without a real security gain); an IAM user with access keys (long-lived keys).

## 75. Terraform gets the `aws login` session through `aws configure export-credentials` (2026-11-17)

- **Decision:** Before running Terraform on the laptop against real AWS, `eval "$(aws configure export-credentials --profile dinogym --format env)"` puts short-lived keys in that shell's environment ([../infra/README.md](../infra/README.md)).
- **Why:** The pinned AWS provider 6.22.0 (decision 58) fails with "No valid credential sources found" on a profile that holds an `aws login` session (`login_session`); its SDK predates that credential type. The exported keys expire with the session and are never written to disk.
- **Rejected:** Access keys in `~/.aws/credentials` (long-lived); upgrading the provider (blocked by aws/aws-emulator#56, decision 58).

## 76. The AWS side runs until February 2027, then is torn down (2026-11-17)

- **Decision:** Staging, prod and the bootstrap stay up while the cloud features are built and used, and are destroyed on 2027-02-01 (a calendar reminder exists). Order: set `DEPLOY_ENABLED` to `false`, destroy `envs/prod` and `envs/staging`, delete the SSM parameters, empty the state bucket's object versions, destroy `infra/bootstrap`.
- **Why:** The project is for learning and showing the work, not a product with users. The account is on the AWS free plan, which ends 2027-03-18 and would need an upgrade to keep running. The app is offline first and release 2.0.0 needs no AWS at all, so nothing the owner uses daily depends on the backend.
- **Rejected:** Keeping it running indefinitely (cost after the free plan, for no users).

## 77. Deploy roles trust GitHub's immutable subject (2026-11-17)

- **Decision:** The deploy roles' trust condition is `sub = repo:Pierorivera1@176773497/DinoGym@1460291141:environment:<env>`: the owner and repository IDs next to the names, which is what GitHub sends for this repository (`gh api repos/Pierorivera1/DinoGym/actions/oidc/customization/sub` shows `use_immutable_subject: true`).
- **Why:** Found by the first staging deploy: the name-only subject from decision 70 was refused with "Not authorized to perform sts:AssumeRoleWithWebIdentity". The IDs are also the stronger check: if the repository is renamed or deleted and another one takes the name, its tokens carry different IDs and are refused. That matters here, since this repository took over the name `DinoGym` from version 1.
- **Rejected:** Turning immutable subjects off for the repository to keep the old condition (weaker); `StringLike` with a wildcard over the IDs (same weakness).
- **Note:** The emulator does not evaluate trust policies, so only real AWS could catch this (decision 70).

## 78. First real deploy: what staging proved (2026-11-17)

- **Decision:** The deploy roles also get `apigateway:TagResource` and `apigateway:UntagResource`. Creating the tagged `$default` stage was refused without them, even with `apigateway:POST` on `/apis/*`.
- **Proven on real AWS:** OIDC sign-in into `dinogym-deploy-staging` (after decision 77), a full Terraform apply of `envs/staging` with the deploy role's permissions, `/health` answering 200, and the API role carrying `dinogym-staging-boundary`. IAM's policy simulator (`aws iam simulate-principal-policy`) on the staging deploy role: `iam:CreateRole` is allowed with the staging boundary, implicitly denied with no boundary or with the prod boundary, and `iam:DeleteRolePermissionsBoundary` is explicitly denied (decision 71).
- **Why record it:** Decisions 70–71 could only say the IAM side was untested; the emulator does not evaluate IAM. This is the first evidence it works.

## 79. Session tokens: a 15-minute JWT and a single-use refresh token (2026-11-17)

- **Decision:** `backend/internal/auth` issues the sessions of decision 10.
  - **Access token:** a JWT signed with HS256 (`github.com/golang-jwt/jwt/v5`). Claims: `iss` = `dinogym-<env>`, `aud` = `dinogym-api`, `sub` = the DinoGym user id, `iat`, and `exp` 15 minutes later. It is checked without any database read, and only HS256 is accepted, so a token claiming `none` or another algorithm is refused before its signature is looked at.
  - **Refresh token:** 32 random bytes, base64url, opaque. Only its SHA-256 is stored: item `REFRESH#<hash>` / `TOKEN` with `userId`, `familyId`, `expiresAt` (epoch ms, 60 days), `ttl` (epoch seconds) and, once used, `usedAt`.
  - **Rotation:** `/auth/refresh` sets `usedAt` with the condition `attribute_not_exists(usedAt)`, so of two requests with one token exactly one wins, then issues a new pair in the same family. A sign-in starts a new family, `FAMILY#<id>` / `META` with `ttl`. If a used token comes back, the family gets `revoked = true`; every refresh renews the family's `ttl` with the condition that it is not revoked, which is the check and the extension in one call. Unknown, expired, used or revoked tokens all get 401 `invalid_token`, without saying which.
  - **Signing key:** `/dinogym-<env>/session-signing-key` (SSM SecureString, created by the owner, decision 69), read once at cold start with `ssm:GetParameters` and decryption. Terraform passes its name in `DINOGYM_SESSION_KEY_PARAM`. On the laptop it comes from `DINOGYM_SESSION_KEY`, or is made up at start with a warning. Keys under 32 bytes are refused.
  - DynamoDB TTL is on for the attribute `ttl`, so expired tokens and families delete themselves for free. The code still checks `expiresAt`, since deletion can lag.
  - The API role and its boundary also get `kms:Decrypt`, only through SSM (`kms:ViaService`), to decrypt the SecureString with the AWS-managed `aws/ssm` key. The key policy alone may not be enough once a permissions boundary is attached (decision 71), and the first staging cold start is what proves it.
  - `/sync/push` and `/sync/pull` check the access token before parsing the request. Which routes need it is read from the contract's `security` at start, not kept in a list; a secured route that was not wrapped stops the server from starting.
- **Why:** A stateless access token costs nothing per request, and 15 minutes limits how long a stolen one works. Hashing refresh tokens means a leaked table does not hand out sessions; a plain SHA-256 is enough because the token is 256 random bits, so there is nothing to guess. Keeping used tokens (instead of deleting them) is what makes reuse detectable: a stolen token and the real one cannot both keep a session alive. HS256 fits because the API is the only service that signs and checks tokens.
- **Rejected:** Opaque access tokens (a DynamoDB read per request); PASETO (less common, harder to defend in an interview); RS256 (a key pair for one service that both signs and verifies); storing raw refresh tokens; deleting a token on use (loses reuse detection); an index per family to revoke its tokens one by one (the family record is enough).
- **Cost:** A phone that sends a refresh, loses the response and retries with the same token is treated as reuse: its family is revoked and the user must sign in again. The Android half must take this into account; a short grace period for a just-used token would fix it, at the cost of a weaker rule. Rotating a signing key ends every session.

## 80. Google ID token verification and accounts (2026-11-17)

- **Decision:** `/auth/google` verifies the Google ID token with `github.com/coreos/go-oidc/v3`: signature against Google's published keys (cached by the library), issuer `https://accounts.google.com` (the library also accepts the scheme-less form Google sometimes uses), `aud` = the Web OAuth client ID, and expiry. Google's discovery document is read once per process, at the first sign-in after a cold start; a failure there is a 500 the app retries, and is not cached. The verifier sits behind a one-method interface so handler tests run without Google.
  - The Web client ID `854486518090-7sc6gs4dmbuo5lgsm9ss9g0p0chedm9a.apps.googleusercontent.com` is not secret (it is in every ID token). It reaches the API as `DINOGYM_GOOGLE_CLIENT_ID`, set from a module variable in `envs/staging`, `envs/prod` and `envs/emulator`.
  - The account item is `GOOGLE#<sub>` / `USER` with `userId`, a new random UUID v4, created with `attribute_not_exists(PK)`. When two first sign-ins race, the loser reads the winner's id instead of overwriting it.
  - Nothing else from Google is stored: no email, no name, no picture. The phone keeps those locally for the account screen. The backend holds only a random id and the opaque `sub`, so a leaked table does not reveal who uses the app.
- **Why:** go-oidc is small (go-jose and oauth2) and is the usual Go library for exactly this check. Mapping `sub` to our own id keeps Google's identifier out of every other item and out of the tokens.
- **Rejected:** `google.golang.org/api/idtoken` (pulls in a large module graph for one check); handling Google's JWKS by hand (key rotation and caching are easy to get wrong); using the Google `sub` as the user id (spreads Google's identifier through the table and the tokens); storing the email (personal data the backend does not need).
- **Known gap:** go-oidc reports a failed key fetch the same way as a bad signature, so if Google's keys cannot be fetched a sign-in gets 401 rather than 500.

## 81. Sign-in on the phone: Credential Manager's "Sign in with Google" button flow (2026-11-17)

- **Decision:** The app gets a Google ID token from `androidx.credentials:credentials` 1.6.0 and `credentials-play-services-auth` 1.6.0 (Credential Manager and its Google Play services provider) with `com.google.android.libraries.identity.googleid:googleid` 1.2.1, which supplies `GetSignInWithGoogleOption` and `GoogleIdTokenCredential`. The server client ID is the Web OAuth client of decision 80, in `BuildConfig.GOOGLE_SERVER_CLIENT_ID`. The token is posted to `/auth/google` through the generated client (decision 60), which now runs on `io.ktor:ktor-client-okhttp` 3.6.1 (same version as the rest of Ktor). Google's display name and email (the credential's `id`) are kept only on the phone, for the ACCOUNT card. The API's base URL is a `BuildConfig` field per build type: debug → staging `https://ypn4asfnil.execute-api.us-east-1.amazonaws.com`, release → prod `https://cm884h7had.execute-api.us-east-1.amazonaws.com`. The app now has the `INTERNET` permission.
- **Why:** These three libraries are what STACK.md's "Credential Manager" choice is made of; Google's own guide for Sign in with Google on Android uses exactly them. `GetSignInWithGoogleOption` is the flow for an explicit button, which is what the approved mock has (sign-in lives only in Settings, never as a prompt at launch). OkHttp is the Ktor engine Ktor recommends on Android and is what most Android apps already ship. Debug talking to staging keeps the owner's daily release build on prod data and lets local builds try changes on staging first.
- **Rejected:** `GetGoogleIdOption` (the one-tap bottom sheet that can appear without a tap; it fits a launch prompt, which the design rules out); the deprecated `play-services-auth` `GoogleSignInClient`; the Ktor `Android` engine (HttpURLConnection, no HTTP/2) and `CIO` (its own TLS stack, less used on Android); reading the URL from Terraform output at build time (couples the app build to infra state; a custom domain or that step can come later).
- **Cost:** `googleid` and the Play services parts it brings are under the Android SDK licence, not open source; the licences screen lists them. `credentials-play-services-auth` 1.6.0 pulls `play-services-identity-credentials` 16.0.0-alpha08 at runtime. The API URLs change if an HTTP API is ever recreated. Google only issues tokens to an app whose signing key's SHA-1 is registered for the Android OAuth client, so CI's debug builds (a throwaway debug key) cannot sign in; local debug builds and release builds can ([android/README.md](../android/README.md)).

## 82. The session is stored encrypted with a Keystore AES-GCM key, in its own DataStore file (2026-11-17)

- **Decision:** The whole session (user id, access and refresh tokens, expiry, name, email) is one JSON value, encrypted with AES-256-GCM under a key generated in the Android Keystore (alias `dinogym_session`, never exported), Base64-encoded, and kept in a separate Preferences DataStore file, `account`, next to the theme's `settings` file. The cipher sits behind a `TokenCipher` interface so the storage is tested on the JVM with a fake. A value that does not decrypt or parse reads as signed out. Signing out deletes the value.
- **Why:** DataStore is how the app already stores device-local settings, so no new storage library. The Keystore key cannot be read out of the phone, so a copy of the app's files is useless without the phone itself. GCM authenticates, so a damaged value fails cleanly. Backups are off (`allowBackup="false"`), so the file never lands on a phone without its key.
- **Rejected:** `EncryptedSharedPreferences` / Jetpack Security (deprecated); storing the tokens in plain DataStore (readable from a rooted phone or a debug backup); a Room table (the session is not workout data and must never sync); requiring user authentication on the key (sync must refresh in the background).

## 83. Refreshing on the phone: single flight, a refresh token is never sent twice (2026-11-17)

- **Decision:** `SessionManager` hands out access tokens to later API calls (sync, task 21), refreshing one minute before expiry. One `Mutex` covers every read and write of the session, so concurrent callers cause one `/auth/refresh`. Before a refresh token is sent, the stored session is marked `refreshPending`. After that:
  - 200: the new pair is stored and the mark cleared.
  - Failures that prove the request never left the phone (unknown host, connection refused, no route, connect timeout, TLS handshake) and 429 from API Gateway's throttle: the session is kept, the mark cleared, and the caller hears "offline".
  - Anything else (401, 400, 5xx, a read timeout, a dropped connection, an unreadable body, the caller cancelled, or the app finding the mark still set after being killed): the token may be spent, so the session is ended locally. Only the account file is cleared; workouts and every other table are untouched.
  - Sign-out is local only; the contract has no revoke endpoint, so the refresh token simply expires on the server after 60 days.
- **Why:** Decision 79 revokes the whole family when a used refresh token comes back. Retrying a refresh whose answer was lost would sign the user out anyway and look like theft in the logs; ending the session at once gives the same result honestly. Airplane mode and dead zones fail before anything is sent, so the common offline case keeps the session.
- **Rejected:** Retrying a refresh automatically (exactly what decision 79 punishes); keeping the session after an ambiguous failure and refreshing later with the same token (same); a grace period for a just-used token on the server (weakens decision 79's rule); a server revoke call on sign-out (needs a contract change; worth adding with sync if wanted).
- **Cost:** A refresh that times out after reaching the server, or a 5xx, means signing in again. With 15-minute access tokens this happens only when a refresh is unlucky.

## 84. Settings' ACCOUNT section without the sync row; the sign-out dialog is the app's confirm sheet (2026-11-17)

- **Decision:** Task 20 builds the ACCOUNT card of `Account.dc.html` without the sync status row and `SYNC NOW`; they come with sync in task 21. The signed-in note says sync arrives in the next update instead of the mock's "saved in your account", which would not be true yet. SIGN OUT asks first in the app's existing `ConfirmSheet` (a window sheet, like delete), with a primary SIGN OUT instead of the danger button, since nothing is lost. Errors (no Google account, offline, server error) show as a line in `danger` inside the card; a closed Google sheet shows nothing. While signing in, the button is disabled and reads SIGNING IN…. Spanish uses ENTRAR CON GOOGLE, shorter than "Iniciar sesión con Google" so it fits on one line.
- **Why:** Showing a sync status before sync exists would be a lie. One dialog style across the app is easier to learn than a centered card used only here.
- **Rejected:** A centered dialog as drawn in the mock (a second dialog style for one screen); a snackbar for errors (the card is where the person is looking, and the message should stay until the next try).

## 85. A second debug OAuth client for Android Studio's debug key (2026-11-17)

- **Decision:** Google Cloud has two Android OAuth clients for `io.github.pierorivera1.dinogym.debug`: one for `~/.android/debug.keystore` (terminal builds) and one for `~/.config/.android/debug.keystore` (the key Android Studio uses on this laptop). Both SHA-1s are in [../android/README.md](../android/README.md).
- **Why:** Found on the first sign-in on the owner's phone (Samsung A16): the Google account sheet closed and the app showed nothing. The installed APK was signed by Android Studio's key, which was not registered, and Credential Manager reports that refusal as a cancellation, so the app cannot tell it apart from the user closing the sheet. With the second client, sign-in, sign-out, reopening the app and the offline message all worked on the phone, and staging stored the user and its sessions.
- **Rejected:** Pointing both tools at one keystore (it changes Studio settings on one machine and is easy to lose); a project-level debug keystore in git (a shared signing key in a public repository).

## 86. Sync ordering: the pull cursor waits 30 seconds behind new rows (2026-11-17)

- **Decision:** Settles decision 51's known risk and the sync index lag of decision 65.
  - A push takes all its change numbers as one block (`ADD seq :n`, one counter update), numbered in the order of its rows, and stores each accepted row with `acceptedAt`, the server's clock right after the numbers were taken. Its writes must finish within 10 seconds of that, or are cancelled.
  - A pull still returns every row it finds after `since`. Its `cursor` moves past rows in order and stops before the first row whose `acceptedAt` is less than 30 seconds old; when it stops early, `hasMore` is false. Those recent rows come again on the next pull, by when any row numbered before them is either written and visible in the index, or was never written.
  - Why this is enough: numbers are taken in time order, so a row with a lower number was numbered no later than one the cursor passes. Thirty seconds covers the 10-second write limit plus the index catching up (usually under a second), with room for clock differences between Lambda instances.
- **Why:** It fixes both causes of a skipped row (two pushes committing out of order, and the eventually consistent index) with one rule, no locks and no extra calls on pull. Applying a pulled row twice already changes nothing (decision 51), so sending recent rows again costs only a few reads. One block of numbers per push halves the counter writes and keeps a 500-row push inside the Lambda's 10-second timeout.
- **Rejected:** Handling one user's pushes one at a time with a lock item (a lease to expire, a second device waiting or failing, and the index lag still unsolved); a transaction per row that commits row and counter together (decision 65: double write cost, and the index lag remains); re-reading a fixed count of numbers behind the cursor (a count says nothing about time, so a slow write can still fall outside it); a counter update per row (double the writes, and a 500-row push takes several seconds of the 10).
- **Cost:** a device that pushes and pulls at once gets its own new rows back, and keeps getting them for 30 seconds. When another device has just uploaded more than one page, a new device gets the first page now and the rest on its next sync.

## 87. Sync handlers: the contract checks the body, rows map through JSON, the whole push fails together (2026-11-17)

- **Decision:**
  - Every JSON request body is checked against its operation's schema in the embedded contract before it is decoded, with kin-openapi (already used for the bearer check). A failure is `400 bad_request` with the field's path and the rule, without the value's surroundings.
  - A push over 500 rows is `400 too_many_rows`; nothing is written. Results come in a fixed table order (programs, cycle steps, routines, exercises, routine exercises, sessions, set entries, weigh-ins, preferences), each list in the order sent.
  - A row becomes an item by way of its JSON: the id (or a preference's key) goes to the sort key, `updatedAt` and `deletedAt` to their own attributes, every other field to an attribute of the same name (strings, numbers kept exactly as written, booleans). A null optional field is not stored and reads back as null. Sort key kinds: `PROGRAM`, `CYCLESTEP`, `ROUTINE`, `EXERCISE`, `ROUTINEEXERCISE`, `SESSION`, `SET`, `WEIGHIN`, `PREF`.
  - The store writes four rows at a time. If any write fails, the push answers `500 internal` without per-row results; rows already written stay, and a retry of the batch gets `stale` for them.
  - A pull leaves out empty tables. A row kind this binary does not know is skipped and the cursor still moves past it.
  - Logs carry counts only, never row contents.
  - The same sync test suite runs on an in-memory fake (unit tests) and on the real store against the emulator (`-tags integration`), so the fake cannot drift from the real rules unnoticed.
- **Why:** Go's JSON decoder turns a missing field into a zero, which would then be stored as data; checking against the contract keeps the rule in one place for both generators. The JSON path keeps the mapping identical for every table and uses the names Room and the contract already use, so a new column needs no backend code. Four workers keep a 500-row push near a second on Lambda without pushing one user's partition towards DynamoDB's 1,000 writes a second. An all-or-nothing error is simple for the device, and safe because a retry is idempotent.
- **Rejected:** Hand-written checks per table (a second copy of the contract to keep in step); kin-openapi's request router (needs another module for routing we already have); a per-row `error` status (a contract change for a case the retry already handles); `attributevalue` from the AWS SDK for the mapping (another module, and its Go field names would not match the JSON names); writing rows one at a time (several seconds of the 10-second timeout for a full batch).

## 88. Phone change tracking: SQLite triggers fill a `sync_pending` table (2026-11-17)

- **Decision:** Database version 3 adds `sync_pending` (`seq` autoincrement, `tableName`, `rowId`, unique on table and row). An `AFTER INSERT` and an `AFTER UPDATE` trigger on each of the nine synced tables run `INSERT OR REPLACE` into it, so every change gives the row's entry a new, higher `seq`. A push reads the pending rows with their `seq`; for `accepted` or `stale` it deletes the entry only where `seq` is still the one it read, so an edit made while the push was on the wire stays pending. The triggers are created with `IF NOT EXISTS` in a Room `onOpen` callback (`SyncTracking`), since Room does not manage triggers. `local_flag`, the theme (DataStore) and the sync tables have no trigger and never sync.
- **Why:** The app has dozens of write paths (logging a set, UNDO, reorders, the seed, editing a past session). A trigger cannot be forgotten by the next DAO method, and the repositories did not change at all. A separate table keeps the synced tables' shape equal to the contract (decision 62) and lets "n changes waiting" be one `COUNT(*)`.
- **Rejected:** A `dirty` or `syncedAt` column on every table (a migration that rebuilds nine tables with data, and every write path must remember to set it); marking rows in each repository (easy to miss one, which silently never syncs); comparing `updatedAt` with the last push time (a phone clock change would skip rows).
- **Cost:** The logic lives in SQL that Room does not check at build time; `SyncLocalStoreTest` (androidTest, CI) covers it. Changing a trigger later needs a `DROP TRIGGER` in that version's migration.

## 89. Pulled pages: one transaction each, parents first, orphans wait in `sync_inbox` (2026-11-17)

- **Decision:** A pulled page and its new cursor are written in one Room transaction. Rows are applied in table order (programs, routines, exercises, cycle steps, routine exercises, sessions, sets, weigh-ins, preferences) with the merge rule of decision 51: newer `updatedAt` wins, a deletion wins a tie, otherwise the phone's row stays, so a newer unpushed edit is never overwritten and re-applying a row is a no-op. A pulled row that wins is written with Room's upsert (update in place, never delete-and-insert, so children keep their parent) and its pending entry is dropped. A row whose parent the phone does not have yet goes to `sync_inbox` (the JSON of a one-row `ChangeSet`) and is tried again after every page.
- **Why:** A set's session can be on a later page: the server keeps one copy of each row with its latest change number, so finishing a workout renumbers the session after its sets, and the 30-second window (decision 86) can hold the session back until the next sync while the set already passed the cursor. Without the inbox the set would hit the foreign key or be skipped forever.
- **Rejected:** Pulling every page into memory and applying them in one transaction (unbounded memory on a first pull); turning foreign keys off while applying (`PRAGMA foreign_keys` cannot change inside a transaction, and orphans would stay invisible in the database); not moving the cursor past an orphan (pulled rows carry no change number).

## 90. First sign-in uploads the whole history; another account gets the phone's data too (2026-11-17)

- **Decision:** `local_flag` keeps `sync_user_id`. When a user signs in whose id differs from it (always true on the first sign-in after this update), every row of every synced table is marked pending, the pull cursor goes back to 0, waiting orphans and the old sync status are cleared, and a sync runs at once. Signing in again with the same account changes nothing, so changes made while signed out sync then (the sign-out dialog says so). Signing out keeps all data and pending marks.
- **Why:** The owner chose that the phone's existing history goes to the account. For a different account, uploading the phone's data and pulling from 0 is the simple rule that loses nothing: the phone is one person's training log.
- **Rejected:** Wiping local data when the account changes (loses workouts); asking which data to keep (a merge dialog for a case that will almost never happen); keeping the old cursor for a new account (it would skip that account's rows).
- **Known gap:** every install seeds the owner's program with new random ids (decision 40), so a second phone or a reinstall that signs in ends up with two copies of the seeded program. Deleting one is a manual step for now (OPEN-ITEMS.md).

## 91. Sync runs from WorkManager: periodic, soon after changes, after sign-in, SYNC NOW (2026-11-17)

- **Decision:** `androidx.work:work-runtime` 2.12.0 (in STACK.md as "Background sync"). Three unique works run the same `SyncWorker`, each needing a connection: `sync-periodic` every 6 hours (KEEP), `sync-now` after sign-in and on SYNC NOW (KEEP), and `sync-soon` 60 seconds after any synced write (KEEP, so a burst of logged sets joins one run). A run that sees rows changed while it ran returns `retry`, so they go up after the 60-second backoff. Offline, 429 and 5xx retry with exponential backoff; 400, a second 401 and a row too big for one request fail and show "Sync failed" until a later run succeeds. `SyncController` follows the signed-in user id from `SessionManager` for the life of the app and cancels the three works on sign-out (including a session that ended on a failed refresh). One `Mutex` in `SyncEngine` keeps runs from overlapping. WorkManager is initialised on demand with a small hand-written `WorkerFactory`, not through `hilt-work`.
- **Push details:** batches of at most 500 rows and about 900 KB of JSON (set notes are unbounded); a 413 splits the batch in two. Pull asks for 1000 rows a page until `hasMore` is false. A 401 asks `SessionManager.accessToken(rejected = token)` for a new token once and repeats the call once; the session manager refreshes only if that token is still the stored one, so the single-flight and never-twice rules of decision 83 hold.
- **Why:** WorkManager runs when the app is closed, survives reboots and waits for a network, which is what offline-first sync needs; nothing in the app waits on it. Unique works with KEEP give the debounce without in-process timers that die with the process.
- **Rejected:** `hilt-work` and its annotation processor (another dependency and processor for one worker); `REPLACE` for the after-change work (it would cancel a running sync every time a set is logged); a sync on every write (a request per set); expedited work (needs a foreground notification below Android 12).

## 92. Starter programs: four, offered from `+ NEW` on the Programs tab (2026-11-18)

- **Decision:** Task 22 ships four starter programs from TRAINING.md: Gym · 5 days and Calisthenics · 5 days (Upper, Lower, Rest, Push, Pull, Legs, Rest) and Gym · 4 days and Calisthenics · 4 days (Upper, Lower, Rest, Upper, Lower, Rest, Rest). `+ NEW` on YOUR PROGRAMS now opens a NEW PROGRAM sheet: EMPTY PROGRAM (the old behaviour) on top, then one card per starter with its routine and exercise counts and its cycle strip. Tapping a starter copies it in (decision 93) and opens it in the program editor, which is the program builder: the existing cycle and routine editors (task 7) edit a starter like any program.
  - Where a TRAINING.md row offers a choice, the first option is used (hack squat, machine chest press, push-up, bodyweight squat); the user renames it if they train the other.
  - Exercises that are the same movement as one in the owner's program use the same name (Dips, Pull-ups, Lateral raises, Bicep curl, Reverse curl, Push-ups, Squats, Bulgarian split squat, Neutral-grip pull-ups, Jump rope), so they reuse his library rows and history.
  - Icons: the closest existing pixel icon, `GENERIC` where none fits (deadlifts, leg curls, calf raises, flies, extensions). Drawing new icons is left for later.
  - The sheet names Jeff Nippard only as the source of the advice and says the calisthenics programs apply the same principles (decision 27).
  - The sheet's mock (`NewProgram.dc.html`) is built before the owner's review, the same exception as decision 41: the owner asked for tasks 22 and 23 in one run and told the AI to make the decisions.
- **Why:** The 4-day layout exists for people with less time, and both program types use it, so offering it for Gym and Calisthenics costs two list entries and no new routines. Putting the starters behind `+ NEW` means a new user finds them where they would make a program, and nothing changes for someone who never opens the sheet.
- **Rejected:** A step-by-step builder wizard (the editors already do everything a wizard would, and a second way to edit programs is more to test and explain); showing starters on Home when there is no program (Home already points to Programs, and the first run always seeds the owner's program); replacing the first-run seed with a starter picker (the owner's program is his first data, decision 27).

## 93. Templates are installed as new rows; exercises are matched by name and kind (2026-11-18)

- **Decision:** `ProgramInstaller` copies a `ProgramTemplate` (plain data, no ids) into Room in one transaction. The first-run seed, the starter programs and task 23's shared-link import all go through it.
  - Every program, routine, cycle step and routine line gets a new random id, so it syncs like any row the user made.
  - An exercise reuses an existing **active** exercise whose name matches ignoring case, accents and outer spaces, and whose kind (reps, template, show-only) is the same. Otherwise a new exercise is created. Inside one template, an exercise used by several routines is one row.
  - A routine or program name already taken (same folding) gets a number: `Upper 2`, `Upper 3`.
  - The new program becomes active only when no program is (decision 48); the first-run seed is always active.
- **Why:** Matching by name keeps one history per exercise, which is what progress and set-1 bests are built on (decision 28): installing Calisthenics · 5 days on the owner's phone reuses his Dips instead of making a second Dips with no history. Requiring the same kind stops a show-only Jump rope from merging with a logged one. Numbering names keeps two "Upper" routines apart in the Programs list without asking the user anything.
- **Rejected:** Always creating new exercises (duplicate library rows and split histories); matching archived exercises (the user put them away; bringing them back silently would be surprising); asking the user to merge exercises during an install (a dialog for a choice almost nobody needs); fixed ids for starter rows (two installs would be the same rows, so editing one copy would edit both).
- **Known gap:** names are matched in the phone's language. A program installed in Spanish on one phone and English on another does not share exercise rows; sync still keeps each phone's rows.

## 94. Starter content lives in code and string resources, resolved when installed (2026-11-18)

- **Decision:** The starter programs are Kotlin data (`data/seed/StarterPrograms.kt`) next to the owner's seed, with every name a key in `strings_seed.xml` (English and Spanish). Names are resolved to the phone's language once, when the program is installed; after that they are user data and can be renamed.
- **Why:** The same pattern as the first-run seed (decision 40): JVM unit tests check the content against TRAINING.md, a missing translation fails to compile, and no parser or file format is needed.
- **Rejected:** A JSON asset (a parser and a schema for four fixed programs, and translations would live outside the string resources); fetching starters from the backend (breaks offline first and adds an API for content that changes with app releases anyway).

## 96. A share is an immutable snapshot of a routine or a program, with nothing about the user (2026-11-18)

- **Decision:** Task 23 shares a **routine**, or a **program** with its cycle and the routines it uses. The app sends a `ShareSnapshot` (contract, `POST /shares`): for each routine its name and lines (`sets`, `repMin`, `repMax`, `restSeconds`), each line carrying a copy of its exercise (`name`, `iconKey`, `kind`, `template`, `weightEnabled`); for a program its `name`, `type` and `cycle`, where each step points to a routine by index, and a step without one is a rest day. The snapshot has a `version` (1); an app that meets a newer one asks to be updated. There are no row ids, no history, no logged weights, no names or emails: the public answer is `id`, `createdAt` and the snapshot. Editing the original later changes nothing; sharing again makes a new link.
- **Why:** A snapshot is the smallest thing that rebuilds a routine on another phone, and it maps one-to-one onto task 22's `ProgramTemplate` (decision 93), so the import is the same install path as a starter program. Copying exercises into each line, instead of a separate exercise list with references, keeps the shape flat and the same as the template. Immutable links are simple to cache and cannot be turned into something else after they were sent.
- **Rejected:** Live links that follow edits (the reader could see a routine change under them, and every edit would need a server write); sharing rows with their ids (the importer must make new ids anyway, decision 93, and ids leak nothing useful); a `oneOf` per kind (generates poorly in Kotlin, decision 62); `nullable` routine indexes in the cycle (with `explicitNulls = false` the app omits nulls, so "left out" is the rest day).

## 97. Shares in the table: `SHARE#<id>`, 128-bit ids, 64 KiB, 20 a day, public reads cached an hour (2026-11-18)

- **Decision:**
  - **Item:** `SHARE#<id>` / `SHARE` with `ownerId`, `createdAt` and `snapshot`, the snapshot's JSON as one string (decision 52 left the shape to this task). It has no `changeNumber`, so it stays out of the sync index. The snapshot is re-encoded from the typed body, so fields the contract does not know are never stored.
  - **Id:** 16 random bytes as 22 base64url characters. Unguessable, so a link is the only way to find a share; a malformed id gets the same 404 as a missing one, without a read. The put is conditional on the key not existing.
  - **Publishing needs a session** (decision 79); **reading is public** (`security: []` on `GET /shares/{shareId}`).
  - **Limits:** the contract caps every list and string (14 routines, 30 lines each, 60 cycle steps, names 120 characters); the handler also refuses a snapshot over 64 KiB (`413 share_too_large`), a cycle pointing past the routines or a routine the cycle never uses, and `repMin` above `repMax`. Each user may publish 20 shares per UTC day: a counter `USER#<id>` / `SHARECOUNT#<date>` is bumped with the condition `n < 20` before the put (`429 too_many_shares`) and deletes itself through TTL two days later.
  - **The link** comes back from `POST /shares` as `url`, built from `DINOGYM_SHARE_BASE_URL`, which Terraform sets to the distribution's address (decision 100). The app never needs to know the domain to share, which matters because the domains exist only after the first deploy. Without the variable, `url` is left out.
  - **Reads** are `ConsistentRead` and answer `Cache-Control: public, max-age=3600`, so CloudFront and browsers keep a share an hour.
  - **No delete yet.** The snapshot holds nothing personal, and deleting from the app needs a list of one's own shares, which nothing else needs.
- **Why:** One string is right for a document written once and read whole; per-field attributes (decision 87) help only rows that are merged field by field. The daily counter is one conditional update, needs no new IAM permission and recovers on its own, unlike a lifetime cap that would need deletes. A strongly consistent read costs half a read unit more and means a link opened right after it was sent cannot get a 404 that CloudFront would then keep for seconds. Caching an immutable answer means a link posted in a group chat costs one Lambda call an hour, not one per viewer.
- **Rejected:** A lifetime cap per user (stuck forever without a delete); WAF rate rules on CloudFront (a web ACL costs about $5 a month plus $1 per rule, half the budget, decision 6); a TransactWriteItems of counter and share (no gain: a counter one too high only costs the owner one share that day); short ids (guessable); storing the snapshot in S3 (a second store and IAM grant for documents of a few KB).
- **Abuse limits:** publishing is capped per account and by the API throttle (decision 67). Public reads of random ids reach the Lambda at most at the throttle's 10 a second, and repeated reads of a real link are answered from CloudFront's cache. A flood of random ids could still use up the throttle that sync shares; accepted for a hobby project, and the budget alert (decision 72) would show it.

## 98. The public page is one static HTML file with plain JavaScript, not React (2026-11-18)

- **Decision:** `web/share/` holds the page for `/r/<id>`: `r.html`, `r.css`, `r.js`, the cursor icon and the app's own Pixelify Sans font file (with its OFL notice). No build step, no library. `r.js` reads the id from the address, fetches `/shares/<id>` from the same origin, and shows the program (type, days, cycle strip) or routine with every exercise as `sets × reps · rest`, in English or Spanish from the browser's language, Night or Day from its colour scheme (DESIGN.md tokens). Every name from the share is set with `textContent`, never `innerHTML`, and CloudFront adds a Content-Security-Policy that allows only the page's own files (decision 100). On Android, `OPEN IN DINOGYM` is an `intent://` link naming the app's package, with the GitHub Releases page as the fallback; `GET THE APP` always links to the latest release. Elsewhere the page says DinoGym is an Android app. `noindex` keeps shared links out of search engines. Terraform fills in the package name per environment (`templatefile`).
- **Why:** STACK.md lists React for the web, but that choice is for the logged-in dashboard of task 25, which has routing, charts and sign-in. This page shows one document and two buttons: about 450 lines of HTML, CSS and JavaScript, readable in an interview, and nothing to build or update. The intent link opens the app even when Android has not verified the App Link (decision 99), and falls back to the APK when the app is missing.
- **Rejected:** React and TypeScript now (a toolchain and a bundle for one static view; task 25 can move the page into its React app if it wants); rendering the page in the Lambda (a server-rendered page per view, which CloudFront could not share between viewers as easily, and HTML in the Go API); Inter as a web font (860 KB; the system sans-serif reads as well); Google Fonts (a third-party request that would tell Google who opened the link).
- **STACK.md** records the page under Web as the exception.

## 99. App Links: `assetlinks.json` per environment; debug links go to staging, release links to prod (2026-11-18)

- **Decision:** Each environment's distribution serves `/.well-known/assetlinks.json`, generated by Terraform from the module's `app_links`: staging (and the emulator) name `io.github.pierorivera1.dinogym.debug` with both laptop debug keys (decision 85); prod names `io.github.pierorivera1.dinogym` with the release key (RELEASE.md). The app's import screen opens from three ways in: an App Link on `/r/*`, the web page's `intent://` button (decision 98), and a link shared to the app or pasted as text.
  - **Hosts:** the CloudFront domains are random and exist only after an environment's first deploy, so they are a manifest placeholder per build type, filled in once known: debug → staging `d1q286cdeyw8ks.cloudfront.net`, release → prod `d1ktti3qymlgmy.cloudfront.net` (first deploy 2026-11-18). The filter has `autoVerify="true"`, so Android opens these links in DinoGym directly once it has checked `assetlinks.json`.
- **Why:** A debug build reads from staging, so a staging link can only be imported by a debug build, and the same for release and prod. Fingerprints are public (anyone can read them from an APK), so they live in the Terraform roots, not in secrets. A wildcard `*.cloudfront.net` filter was considered for the time before the domains existed, but it can never verify (Android would check `cloudfront.net` itself) and would catch every other site's CloudFront links.
- **Rejected:** One domain for both apps (a debug build would fetch a prod link from the staging API and get a 404); a custom domain (decision 23); guessing the hosts or reading them from Terraform at build time (couples the app build to infra state, decision 81).
- **Consequence:** A recreated distribution changes its domain: the links already sent stop working and the app needs a new build (decision 23). CI's debug builds use a throwaway key, so verification never passes for them; the other two ways in still work.

## 100. CloudFront in front of a private S3 bucket and the HTTP API (2026-11-18)

- **Decision:** The module's `web.tf` creates, per environment, the bucket `dinogym-<env>-web-<account>` (private, public access blocked) and one distribution with two origins: the bucket through origin access control, and the HTTP API. Behaviours: `/shares/*` goes to the API, GET and HEAD only, with a cache policy that follows the API's `Cache-Control` and keys on nothing else; everything else goes to S3 with the managed CachingOptimized policy. A CloudFront Function (viewer request) rewrites `/r/<id>` to `/web/r.html`. A response headers policy adds a Content-Security-Policy (`default-src 'none'`, own scripts, styles, fonts and fetches only), `nosniff`, `DENY` framing, `no-referrer` and HSTS. HTTP redirects to HTTPS. `PriceClass_100`. The page's files are cached 5 minutes, so a deploy reaches people without invalidations. The output `web_url` is the link base.
- **Deploy role permissions:** `infra/bootstrap` gives each deploy role S3 bucket and object actions on `dinogym-<env>-web-*` only, and the CloudFront actions above on every CloudFront resource in the account: like API Gateway (decision 70), CloudFront's ARNs hold random ids. The Lambda's role and the boundary do not change. The owner applies the bootstrap by hand before this reaches `main`.
- **Why:** One origin for the page and its data means no CORS and no API URL in the page. Publishing goes to the API directly from the app, so CloudFront only ever forwards reads. A function is the only way to serve one object for many paths without a custom error page, which would apply to the whole distribution and turn the API's 404s into the page.
- **Rejected:** S3 static website hosting (needs a public bucket and serves only HTTP); a custom error response for the rewrite (above); Lambda@Edge (slower, costs more, and runs in many regions); `PriceClass_All` (South American edges would be faster for the owner, but cost more per GB for a page of 100 KB); tag conditions on the CloudFront statement (not verified on real AWS before this task, and a wrong guess costs another bootstrap round trip).
- **Cost:** the CloudFront always-free tier is 1 TB out, 10 M requests and 2 M function invocations a month; S3 holds about 100 KB. Expected: $0.

## 101. The emulator serves CloudFront distributions: what that proves (2026-11-18)

- **Found on 1.0.491:** besides creating the S3 bucket, origin access control, CloudFront Function, cache and response headers policies and the distribution (apply, an empty second plan, destroy), the emulator **serves** the distribution on a port inside its container (`web_url` is `https://127.0.0.1:<port>`). There, `/r/<id>` returned the page through the function's rewrite, a missing S3 key returned 403 through origin access control, `/shares/*` reached the emulator's API Gateway, a POST was refused by the GET-only behaviour, HTTP was redirected to HTTPS, and the CSP header was added.
- **Decision:** `infra/emulator/check.sh` runs these nine requests with `docker exec aws-emulator curl` after the apply, on every pull request.
- **What it does not prove:** that real CloudFront and S3 behave the same; the bucket policy and origin access control on real AWS (the emulator does not evaluate IAM or bucket policies, so a 403 there is not evidence the policy is right); the Lambda answering through CloudFront (Go cannot run on the emulator, decision 14); TLS, edge caching or speed; App Link verification by Android. Staging's smoke test covers the first four (`deploy.yml`), a phone the last.

## 102. Importing a shared link on the phone: preview first, then the task 22 installer (2026-11-18)

- **Decision:** The import screen (`ui/share/`) opens from an App Link or the web page's intent link (`ACTION_VIEW` on `/r/*`), from text shared to DinoGym (`ACTION_SEND`, `text/plain`), or from FROM A LINK in the Programs tab's `+ NEW` sheet, where a link is pasted. `ShareLinks` takes the 22-character id from any `https://<host>/r/<id>` in the text; the share is read from the build's own API (`GET /shares/{id}`, no account). The preview shows what is shared, the cycle, every routine with its exercises, NEW on exercises the library does not have, and a text box saying what ADD will do. ADD copies it in through `ProgramInstaller` with task 22's rules (decision 93): new ids, exercises reused by folded name and kind, taken names numbered, and a program becomes active only when none is (decision 48). A single routine goes in through the new `installRoutine`, which joins no program. The new program's or routine's editor then replaces the import screen. The new rows sync like any others.
  - **Offline:** reading needs a connection, so the screen says so with TRY AGAIN; adding itself is local. Unknown ids, a newer snapshot `version`, a cycle pointing at a missing routine or an enum this app does not know read as "made with a newer DinoGym" or "not found", never a crash.
  - Signed out works: reading is public.
- **Why:** One install path means a starter program and a shared one behave the same, and the matching rules are tested once. A preview before writing anything lets a person see what a link holds, and NEW tells them what joins their library.
- **Rejected:** Importing straight from the link without a preview (a tap on a chat link would change the library); keeping the snapshot's exercise ids (they are the sender's rows); creating every exercise anew (split histories, decision 93); asking which existing exercise to merge with (a dialog nobody needs).

## 103. Sharing from the editors: SHARE ROUTINE / SHARE PROGRAM, then Android's share sheet (2026-11-18)

- **Decision:** A secondary SHARE ROUTINE button ends the routine editor's list, and SHARE PROGRAM sits under the program editor's cycle. It saves a name still being typed, reads the routine or program from Room as a template (a program's routines once each, in cycle order), checks the contract's limits on the phone, and posts it. A window sheet shows the state: making the link; the link with SEND LINK, which opens Android's share sheet with "<name> on DinoGym: <link>" (it also opens once on its own); signed out, with a button to Settings; offline and server errors with TRY AGAIN; 20 links today, too big, or a program with no routine in its cycle. Publishing uses the session like sync: a 401 refreshes the token once and tries once more (decisions 83 and 91).
- **Why:** The top bar has no room for a third 48dp button on a 360dp phone without cutting `EDITAR PROGRAMA`, and a labelled button at the end of the list is easier to find than an icon. Showing the link in the sheet means closing Android's share sheet by mistake does not lose it.
- **Rejected:** A share icon in the top bar (above); sharing without an account (anyone could fill the table, decision 97); sharing a routine from the Programs list (two places for one action); copying to the clipboard only (Android's share sheet already offers Copy).
- **Known gap:** a name typed in the last half second before tapping SHARE is written in the background and may miss the snapshot; the link then carries the name as it was a moment before.

## 104. Reminders are sent by the server; the phone writes the words (2026-11-18)

- **Decision:** Task 24 has two reminders, each with its own switch in a new Settings › REMINDERS section (`Reminders.dc.html`): **workout day**, at a time the user picks (default 8:00), and a **weekly summary** on Sundays at 20:00. Both need an account. The server decides *when* and *to whom*; it sends an FCM **data message** that holds only the kind (`workout_day` or `weekly_summary`). The app's messaging service builds the notification from Room and string resources: the routine at the active program's current step and its exercise and set counts, or the week's workouts, sets and time trained plus what is up next. It shows nothing on a rest day, when there is no active program, or when a workout already ended that day. Each kind is its own notification channel; tapping opens Home.
- **Why:** The phone has the freshest data (an edit not yet synced still counts), the rules (cycle, rest day) already live in Kotlin and are tested there, and every string stays in the English and Spanish resources (CLAUDE.md). The server needs no copy of the cycle rules and reads none of the user's workouts. A push from the server arrives at the chosen minute even in Doze, which an inexact local alarm does not promise, and the setting follows the account to every phone.
- **Rejected:** Server-written notification text (Go copies of the cycle rules, and strings outside the Android resources); local alarms only (simpler and works signed out, but no exact time without the exact-alarm permission on Android 12+, and decision 7 chose server-sent reminders as the cloud feature to build); a reminder per day of the week (the cycle does not follow weekdays, decision 29).

## 105. Settings travel as synced preferences; one EventBridge schedule per user and kind (2026-11-18)

- **Decision:**
  - The settings are four `preference` rows, so sync carries them with no new endpoint and they work offline: `reminder_workout_day` and `reminder_weekly` (`true`/`false`), `reminder_time` (`HH:mm`) and `reminder_time_zone` (IANA, such as `America/Lima`). The app writes the zone when a switch is turned on and again whenever the phone's zone differs at start.
  - After a push accepts a row whose key starts with `reminder_`, the API **reconciles**: it reads the four stored rows and creates, updates or deletes the user's schedules in the group `dinogym-<env>`: `<userId>-day` (`cron(mm HH * * ? *)`) and `<userId>-week` (`cron(0 20 ? * SUN *)`), both with `ScheduleExpressionTimezone` set to the user's zone. Each targets the reminder Lambda with `{"userId", "kind"}`, a retry policy of 2 attempts within an hour, and no dead-letter queue. A reconcile that fails is logged and does not fail the push; `PUT /devices` (decision 106) reconciles again, so the next app start repairs it.
  - At fire time the reminder Lambda checks the stored setting is still on before sending, so a schedule left behind by a lost update sends nothing.
- **Why:** Scheduler applies the time zone and daylight saving itself and is built for one schedule per entity (a million per account); nothing polls. The free tier is 14 million invocations a month, and one user uses about 35. Reconciling from the stored rows, not from the pushed one, makes the call idempotent and order-proof.
- **Rejected:** One cron every 15 minutes that finds the users due (a query or index over every user's settings, time zone arithmetic in Go, and 2,880 runs a month mostly doing nothing); DynamoDB Streams to trigger the reconcile (a new service and an event source for four rows); a dedicated `PUT /reminders` endpoint (settings would need their own offline queue and multi-device merge, which sync already does); Scheduler → SQS → Lambda (a queue adds nothing that Scheduler's retry policy does not, at this volume).

## 106. Devices register their FCM token: `PUT` and `DELETE /devices/{installationId}` (2026-11-18)

- **Decision:** A signed-in app sends `PUT /devices/{installationId}` with its FCM token at start (at most once a day unless the token changes) and from `onNewToken`; sign-out sends `DELETE` first. The item is `USER#<id>` / `DEVICE#<installationId>` with `token`, `updatedAt` and a `ttl` 60 days ahead, so a phone that stops opening the app drops out by itself. The installation id is a random UUID kept in `local_flag`. The reminder Lambda queries `DEVICE#` and sends to every token; a token FCM answers `UNREGISTERED` (or 404) for is deleted. Device items have no `changeNumber`, so they never enter the sync index.
- **Why:** Reminders go to every phone of the account. Keying by installation, not by token, means a refreshed token overwrites its old one instead of piling up.
- **Rejected:** Syncing tokens as preferences (a phone's token is not a user setting, and every phone would pull every other's); FCM topics per user (topic subscriptions are made from the phone, so any app could subscribe to any user's topic name).

## 107. The reminder Lambda is its own function and role, reaching FCM through decision 55 (2026-11-18)

- **Decision:** `backend/cmd/reminders` builds a second binary, deployed as `dinogym-<env>-reminders` (`provided.al2023`, arm64, 128 MB, 30 s timeout, 7-day logs). Its role may Query and DeleteItem in its own table and nothing else; the API's role gains `scheduler:Create/Update/Delete/GetSchedule` on `schedule/dinogym-<env>/*` and `iam:PassRole` on the Scheduler role, which may only invoke the reminder function. FCM is called over plain HTTPS (`messages:send` of the HTTP v1 API) with a Google token from workload identity federation: `golang.org/x/oauth2/google/externalaccount` exchanges the function's own AWS credentials (passed from the AWS SDK as a credential supplier) for a short-lived token of the `fcm-sender` service account. The audience and service account are not secret and are Lambda environment variables.
- **Google side:** `infra/google/` is a separate Terraform root, applied by the owner by hand with `gcloud auth application-default login` like `infra/bootstrap`. It enables the APIs, creates the workload identity pool with an AWS provider for this account whose attribute condition admits only the `dinogym-staging-reminders` and `dinogym-prod-reminders` roles, and the `fcm-sender` service account with only `roles/firebasecloudmessaging.admin`. Its state goes in the existing state bucket.
- **Why:** The function that can reach Google has no access to SSM, sessions or sync writes, and the API, which faces the internet, cannot send messages. The Firebase Admin SDK is not needed for one HTTPS call. Google calls are not in the emulator, so the FCM client is tested against a local fake server; staging proves the federation.
- **Rejected:** One binary with a mode switch (one role would hold both permission sets); a service-account key (decision 55); the Firebase Admin SDK (large, and its federation support goes through the same oauth2 package anyway); applying the Google root from CI (CI would need its own Google federation for a root that changes once).
- **Cost:** $0: Lambda, Scheduler, FCM and workload identity federation are free at this volume.

## 108. Reminders backend as built: details settled in the code (2026-11-18)

- **Decision:**
  - **Contract:** `PUT /devices/{installationId}` (body `DeviceRegistration`, `token` 1–4096 characters) and `DELETE /devices/{installationId}`, both 204 and both behind the bearer check. The id must be a UUID; the handler checks it, since the generated code validates bodies, not path parameters. A malformed id on DELETE is a 204 with nothing deleted.
  - **Reconcile** updates a schedule first and creates it only on `ResourceNotFoundException`, since after the first time nearly every call is an update. Settings with a bad time or an unknown time zone remove both schedules rather than schedule at a wrong hour. Go's zone database is embedded (`time/tzdata`), because `provided.al2023` has none.
  - **Fire** returns an error, so Scheduler retries, only when no phone was reached for a reason worth retrying; otherwise a retry would send twice to the phones that got it. FCM messages are `android.priority: high` with a one-hour TTL.
  - **Google federation** is configured with discrete values (`DINOGYM_GCP_AUDIENCE`, `DINOGYM_FCM_SERVICE_ACCOUNT`) instead of a credential file, and the AWS SDK's credentials are handed to Google's library through `AwsSecurityCredentialsSupplier`, so the SDK's caching and refresh apply. The pool is `dinogym-aws`, its provider `aws-account` (Google wants at least 4 characters), and its attribute condition admits only the two reminder roles by ARN prefix.
  - **IAM:** the Scheduler role's trust is limited to schedules in its own group and account (`aws:SourceArn`, `aws:SourceAccount`). The bootstrap's boundary gains DeleteItem, the schedule actions on `schedule/dinogym-<env>/*`, passing `dinogym-<env>-scheduler` to Scheduler and invoking `dinogym-<env>-reminders`; the deploy roles may pass the reminder role to Lambda and manage the schedule group. **The owner applies the bootstrap by hand before this reaches `main`**, as in decision 100.
- **Tested:** unit tests with a fake Scheduler and a local fake of FCM's `messages:send` (success, `UNREGISTERED`, outage); the device store, the settings read and the reconciler against the emulator's DynamoDB and Scheduler; `check.sh` applies, re-plans clean and destroys the module with the new resources. **Not provable on the emulator** (decision 14): a schedule invoking the Go Lambda, the token exchange with Google, and delivery to a phone. Staging and a phone prove those.
- **Rejected:** creating first and updating on `ConflictException` (one wasted call on every change after the first); a credential configuration JSON in an environment variable (the same values, harder to read in the console and in Terraform).

## 109. Reminders on the phone: Firebase from BuildConfig, registration and the REMINDERS section (2026-11-19)

- **Decision:**
  - **Firebase without the Google Services plugin.** The app starts Firebase itself (`FirebaseApp.initializeApp` with `FirebaseOptions`) from BuildConfig fields: project id, sender id and the per-build-type app id (`.debug` and release are separate Firebase apps) are in `build.gradle.kts`, like the Google sign-in client id (decision 80). Only `firebase-messaging` (Firebase BoM 35.2.0) is added. The API key comes from `DINOGYM_FIREBASE_API_KEY` or the git-ignored `app/google-services.json`; a build without it (CI's lint and unit tests) skips Firebase and reminders are off. The release workflow fails without the secret.
  - **Registration.** `FcmDeviceRegistry` follows the account: every sign-in, every app start and every `onNewToken` sends `PUT /devices/{installationId}`, skipped when the same account and token were sent less than a day ago. The installation id and what was last sent live in `local_flag`. A 401 refreshes once and retries (decision 91's rule). Sign-out sends `DELETE` first, with a 5-second limit, then clears the session; a message that still arrives signed out is dropped.
  - **Settings.** The four preference rows are written by `ReminderRepository`; every change also writes the phone's zone, and app start rewrites the zone when a reminder is on and the phone moved. REMINDERS sits between ACCOUNT and THEME; the TIME button shows only while Workout day is on; turning a switch on asks for `POST_NOTIFICATIONS` when it is missing, and ALLOW NOTIFICATIONS opens Android's notification settings once the dialog can no longer be shown.
  - **Notifications.** `ReminderMessagingService` hands `kind` to `ReminderNotifier`, which reads Room through the same `HomeStateMapper` as Home and words the result from string resources. The weekly summary counts Monday to today in the phone's zone; with no workouts it names the routine up next, and with neither it shows nothing. Channels `reminder_workout_day` and `reminder_weekly`, default importance.
- **Why:** The plugin's only job here would be turning `google-services.json` into resources; doing it in five lines keeps the key out of the public repository (GitHub's secret scanning flags Google API keys), keeps CI building without it, and matches how the app already carries Google ids. The once-a-day limit keeps app starts from calling the API while still refreshing the 60-day TTL (decision 106).
- **Rejected:** the Google Services plugin with `google-services.json` committed (the key in git for no gain); the plugin with the file restored from a secret in every workflow (CI's lint and test jobs would need the secret too); `kotlinx-coroutines-play-services` for one `await` (a ten-line extension does it); asking for the notification permission on opening Settings (asking when a reminder is turned on explains why).
- **Tested:** unit tests for the time and switch encoding, what each reminder says (rest day, mid-workout, trained today, the week's bounds in the zone), registration (once a day, new token, other account, offline retry, 401 refresh, sign-out DELETE) and that sign-out unregisters while the session is still there; Roborazzi screenshots of the section signed in, signed out, blocked and the time sheet. **Not tested here:** delivery from FCM to a phone, which the staging check in OPEN-ITEMS covers.

## 110. The web dashboard reads synced data and computes in the browser; it does not edit (2026-11-20)

- **Decision:**
  - **Scope.** Task 25's dashboard shows what the app's Stats tab shows, in three views with their own URLs: HISTORY (the landing view, with stat panels and an 18-week day grid that a phone screen has no room for), PROGRESS (one exercise at a time; the chart adds every workout's set 1 under the stepped best line) and WEIGHT. It is read-only: no logging, editing or deleting, and each view says changes are made in the app. Mocks: `WebHistory`, `WebProgress`, `WebWeight`, `WebPhone` and `WebSignIn` (`.dc.html`), approved by the owner 2026-11-20.
  - **Data.** No new endpoint. After sign-in the page pages through `GET /sync/pull` from `since=0` until `hasMore` is false, keeps the rows in memory, drops deleted ones and computes history, progress and body weight in TypeScript with the rules of decisions 47 and 49 (set 1 counts; a record beats the best; the 7-day average). REFRESH pulls again from the last cursor. Nothing is stored in the browser beyond the session.
  - **Sign-in.** Google Identity Services' own button gets a Google ID token, which goes to the existing `POST /auth/google` with the same Web client ID as the app (decision 80). How the browser keeps the session (memory only, or the refresh token in storage) is settled when the web app is built, with decision 79's single-use refresh tokens in mind.
  - **Look.** The app's tokens, stepped corners and Pixelify Sans; body text in the system sans-serif, as on the shared link page (decision 98). Night or Day and English or Spanish follow the browser. Served from the root of the same CloudFront distribution as `/r/<id>`, so there is no second distribution or certificate.
- **Why:** The phone is the only writer, so the dashboard adds no conflict cases to sync and needs no offline queue. A user's whole history is a few thousand small rows, so one pull is quick and the progress rules can be ported as they are, with their tests, instead of being written again in Go. Reusing the share page's host keeps the cost at one bucket prefix and the free CloudFront tier.
- **Rejected:** Editing on the web (a second writer with its own conflicts, for a feature the owner does not need at a desk); a stats endpoint that computes in Go (a second copy of the progress rules, and the API would read workouts it otherwise never interprets); an overview page separate from HISTORY (the panels make HISTORY the overview); a custom-styled Google button (Google's branding rules ask for its own button, and GIS draws it in an iframe).

## 111. Web dashboard toolchain and session (2026-11-20)

- **Decision:**
  - **Build:** Vite 8 with React 19 and TypeScript, in `web/dashboard/`. npm (it comes with Node) with a committed `package-lock.json` and exact versions; Node 26 pinned in `.nvmrc`, which mise and `actions/setup-node` both read. Unit tests with Vitest. CI: `.github/workflows/web.yml`.
  - **TypeScript 5.9.3, not 7.0.** The contract types come from `api/openapi.yaml` through `openapi-typescript` (types only, nothing at runtime), committed as `src/api/schema.d.ts` and checked for drift in CI, as the Go server is (decision 60). Its latest release requires TypeScript 5. Move to 7 when it allows it.
  - **No router and no chart library.** Three views and the browser's history API are a few lines; the charts are stepped pixel lines drawn as SVG (DESIGN.md), which chart libraries draw against.
  - **Same origin.** The page calls `/auth/*` and `/sync/*` on its own host: CloudFront sends them to API Gateway, as it already does for `/shares/*`, so the API needs no CORS. Vite's dev server proxies the same paths to staging.
  - **Session in memory only.** Closing the tab signs out; Google's button signs back in with one click, or by itself with `auto_select`. Refreshing follows decision 83 with one change: the browser cannot tell a request that never left from one whose answer was lost, so a failed refresh ends the session unless the browser reports being offline or API Gateway answered 429.
- **Why:** Vite is the default way to build a React app and Vitest shares its config. Keeping the session in memory means an injected script finds no refresh token to steal (60 days of access), and two tabs never spend each other's single-use refresh token (decision 79).
- **Rejected:** Create React App (unmaintained) and Next.js (a server for a static, signed-in page); pnpm or Bun (another tool to install for no gain at this size); hand-written row types (they drift from the contract); TypeScript 7 without generated types; React Router and Recharts or Chart.js (dependencies for little code); the refresh token in `localStorage` (readable by any script on the page) or an `HttpOnly` cookie (needs the API to set cookies and CSRF protection, a contract change); CORS on the API (a second way in to configure and test).

## 112. Web dashboard views: URLs, phone layout, charts and a dev-only demo account (2026-11-21)

- **Decision:**
  - **URLs.** `/history` (newest workout open; on a phone, the list), `/history/<day>` for one workout, with `-2`, `-3` for later workouts the same day, `/progress/<slug>` with a slug of the exercise's name (the exercise trained first keeps the plain slug; repeats get `-2`), and `/weight`. `/` becomes `/history`. Navigation is `history.pushState` and a `Link` that is a real `<a href>`, so new tabs and bookmarks work (decision 111: no router). Serving every path with `index.html` is CloudFront's job in the deploy PR; Vite's dev server already does it.
  - **One code path for both widths.** Below 760 px the same views switch to the `WebPhone` layout: the account becomes a button with REFRESH and SIGN OUT behind it, a workout opens as its own page with a back button, the exercise list becomes a `<select>`, and the 18-week grid is left out. A CSS media query does the styling; a `matchMedia` hook picks the structure.
  - **Charts.** Hand-drawn SVG (`ui/charts.ts`, pure functions with tests; `ui/Chart.tsx` draws them). The width follows the container through `ResizeObserver`; the y axis picks whole, half, 2, 5, 10… steps for at most about six ticks; month labels thin out on narrow charts. Progress plots kilograms for an exercise that uses weight and reps otherwise, as the app's chart does. A shorter range starts the stepped best line at the best it began with. Each chart has a text summary as its `aria-label`.
  - **What the panels count.** GAIN is from the best at the start of the range (or the first record in it) to the best now; RECORDS and the records list are the records inside the range. CHANGE is the latest 7-day average minus the first one in the range, green when it moves toward the goal.
  - **Pixelify Sans** is bundled by Vite from the app's `res/font/pixelify_sans.ttf`, so the dashboard and the app share one copy. Body text uses the system sans-serif (decision 110).
  - **The name** in the header is read from the Google ID token's claims (`given_name` and the first letter of `family_name`), for display only, and kept in memory with the session.
  - **A demo account for development:** `npm run dev`, then `/?demo` (or `/?demo=empty`). It is loaded with a dynamic import behind `import.meta.env.DEV`, so `npm run build` leaves it out (checked: the built bundle has no demo data).
- **Why:** Staging has no synced data from the owner, so the views could not be reviewed from real sign-in; a fixed made-up account shows every state and both languages without a backend. A slug from the name reads better in the address bar than a UUID, and the trained-first rule keeps links stable as new exercises are added. One set of views for both widths keeps the rules in one place.
- **Rejected:** UUIDs in URLs (unreadable, and the mock shows names); separate phone components (two copies of the same rules); a chart library (decision 111); a fixture server for local development (more moving parts than a dev-only dataset); copying the font into `web/dashboard/public` (a second copy to keep in step).
- **Tested:** unit tests for the URLs and slugs, the chart geometry and axes, date and number formatting in both languages, and the range rules for progress and body weight; type-check and build. Looked at in headless Chromium at desktop and 500 px widths, Night and Day, English and Spanish, with the demo account and with nothing synced. **Not tested here:** a real phone (headless Chromium does not go below 500 px) and real data, which the owner's review in OPEN-ITEMS covers.

## 113. The web dashboard opens in Day, with a NIGHT / DAY switch (2026-11-21)

- **Decision:** The dashboard ignores the browser's dark or light setting and opens in Day. A NIGHT / DAY button switches. It sits in the header, in the phone's account menu and on the sign-in page, and names the theme it switches to, as the app's theme options are named. The choice is kept in `localStorage` (`dinogym.theme`) and applied before the first render, so a saved Night does not flash Day. Night is `data-theme="dark"` on `<html>`, which redefines the same CSS tokens. Google's button is redrawn in its matching style. This replaces "Night or Day follows the browser" in decision 110, for the dashboard only. The shared link page still follows the browser.
- **Why:** The owner asked for Day as the default with a way to switch. Remembering a display preference is a per-browser convenience, not account data, so it does not go against decision 110's "nothing stored beyond the session": no rows, tokens or names are stored. If storage is blocked, the page starts in Day and the switch still works for that visit.
- **Rejected:** following the browser with a manual override (three states, where the owner wanted Day first); syncing the choice as a preference row (the dashboard does not write, decision 110); an inline script in `index.html` to apply it even earlier (a content security policy may forbid inline scripts, and the bundle sets it before anything is drawn anyway).

## 114. Web dashboard: an example account, no theme switch on sign-in, the cursor's colours (2026-11-21)

- **Decision:**
  - **SEE AN EXAMPLE**, below Google's button, opens the dashboard with a made-up account: three routines and seven exercises over the last 60 days, about four workouts a week, three weigh-ins a week moving toward a goal. The rows are built in the browser (`src/example.ts`) with a fixed seed and dates relative to today, so the example is the same on every visit and always looks current. Exercise names follow the browser's language. Nothing is sent to the API, and nothing is stored. In the example, an EXAMPLE tag replaces "Updated" and REFRESH, a text box says the workouts are made up, and SIGN IN (back to the sign-in page) replaces SIGN OUT. It replaces decision 112's dev-only `/?demo`.
  - **Charts open on the shortest range that holds all the data** (3M for a two-month account) and otherwise on the mock's default (6M for progress, 3M for weight), so a new account does not open on a mostly empty chart.
  - **No NIGHT / DAY on the sign-in page.** It uses the saved choice (Day by default). The switch stays in the header and the phone's account menu.
  - **Google's button in Night.** Its iframe is a light page; inside a `color-scheme: dark` page the browser paints it opaque white, which showed as a white frame around the dark button. The button's container is now `color-scheme: light`.
  - **The cursor** keeps Day's colours in both themes (yellow face, dark outline). In Night it sits on a small stepped cream tile instead of taking a white outline.
  - **Back/forward cache.** When the browser restores the page from its back/forward cache, the saved theme is applied again.
- **Why:** The owner asked for a way to see the dashboard without an account, for the switch to stay off the sign-in page, and for the cursor to look the same in both themes. A generated example needs no backend, cost or seed data in DynamoDB, and it cannot leak anyone's data. Two months keeps the example small and believable.
- **Rejected:** a public demo account on the API (a shared user anyone could sign into, with data to maintain in every environment); a fixed JSON file of rows (dates would go stale); keeping `/?demo` as well (two ways to show the same thing).
- **Tested:** unit tests for the example (size, two-month span, both languages, deterministic, records present) and for the initial range. Driven in headless Chromium through the DevTools protocol: SEE AN EXAMPLE, the three views, and SIGN IN back to the sign-in page, in Day and Night, desktop and 390 px phone emulation, English and Spanish. **Not checked here:** the white frame around Google's button, which headless Chromium does not reproduce; the owner checks it in Night in a real browser.

## 115. README written as the finished project, with generated architecture diagrams (2026-11-21)

- **Decision:**
  - **README.** Rewritten in the layout of the owner's AprendEZ README (centered title, badges, section links), describing DinoGym as it will be once tasks 25 and 26 are done. Screenshots sit next to the features they show, and all come from the real code with made-up data: the phone screens are the Day-theme Roborazzi renders from the app's screenshot tests (the Day tests render in Spanish; the owner chose them over adding English Day tests), the web dashboard images were captured from `npm run dev` with decision 112's demo account, before decision 114 replaced it with SEE AN EXAMPLE (refresh them from SEE AN EXAMPLE), and the shared routine page is `web/share/` in headless Chromium with a fake `/shares/<id>` response. None show the owner's data. Refresh them from the same sources when a screen changes.
  - **Diagrams.** Three diagrams, architecture, CI/CD, and keyless access with observability, made with the `stack-diagram` skill (caru-ini/stack-diagram v0.7.0), using AWS's official icons. The light PNGs are committed in `docs/architecture/`, with the generator `src/gen.py` and the HTML drafts. The icon files are not committed (redistribution is unconfirmed) and are fetched on each rebuild ([architecture/README.md](architecture/README.md)). The skill itself is installed in the git-ignored `.claude/skills/`.
- **Why:** A reviewer for a cloud or DevOps role reads the diagrams first: request path, deploy pipeline and how credentials work without keys. Generated diagrams from a checked-in source can be redrawn when the stack changes, which a hand-drawn image cannot. The diagrams leave out SQS, because the stack does not use it yet.
- **Rejected:** Mermaid in the README (no vendor icons, so the AWS view reads as generic boxes); draw.io files (hand-placed, with nothing to regenerate from); a single screenshots section (the owner asked for images beside the features); dark-theme variants with `<picture>` (the owner chose the light diagrams).

## 116. The web dashboard is served from the shared link distribution and deployed with the backend (2026-11-21)

- **Decision:**
  - **Files.** `npm run build` (base `/app/`) produces `web/dashboard/dist`. Terraform uploads `index.html` as `app/index.html` with `Cache-Control: no-cache`, and every file under `assets/` (hashed names) with a one-year immutable cache. A missing build fails `terraform plan` (`filemd5` of `index.html`) instead of deleting the live site.
  - **Addresses.** The existing CloudFront Function now also rewrites `/`, `/history…`, `/progress…` and `/weight…` to `/app/index.html`, so a bookmarked or reloaded view loads. Other unknown paths are still a 403 from S3.
  - **Behaviours.** The default behaviour serves the dashboard with its own response headers policy. `/r/*`, `/web/*` and `/.well-known/*` keep the shared link page's stricter policy. `/auth/*` and `/sync/*` go to API Gateway:
    - They allow every method, since CloudFront has no GET+POST set.
    - A zero-TTL cache policy keys on `Authorization` and the query string. It forwards them; nothing is cached, because the API sends no `Cache-Control` on these routes.
    - The managed AllViewerExceptHostHeader origin request policy forwards `Content-Type` and keeps API Gateway's own Host.
  - **Dashboard headers.** The CSP allows only our files plus Google Identity Services' published hosts (`/gsi/client` script, `/gsi/style`, `/gsi/` frames and connections). The referrer policy is `strict-origin-when-cross-origin`, because GIS checks the page's origin. HSTS, `nosniff` and `DENY` framing are as on the share page.
  - **Pipeline.** `deploy.yml`'s build job builds the dashboard once, next to the Lambda zip, and staging and prod deploy that same artifact. A new smoke test requests `/`, a deep link, the script, and `/sync/pull` without a token (a 401 `invalid_token` from the Lambda through CloudFront). `check.sh` builds the dashboard before `apply` and checks the same paths on the emulator's CloudFront. `infra.yml` installs Node for that.
- **Why:** One distribution and one bucket keep the cost at the free CloudFront tier (decision 110), and same-origin API calls need no CORS (decision 111). Uploading through Terraform matches the shared link page: one tool owns every object, and a plan shows what a deploy changes. No new IAM: the deploy roles can already write `dinogym-<env>-web-*/*` and manage CloudFront policies, so `infra/bootstrap` needs no hand-applied change.
- **Rejected:**
  - `aws s3 sync` in the workflow: a second owner of the bucket's objects, invisible to `plan`.
  - The dashboard at the bucket root: its `assets/` would sit beside `web/` and `.well-known/` with nothing marking whose they are.
  - A custom error page that serves `index.html` for 403/404: it would also rewrite the API's own 404s.
  - Forwarding `Authorization` through an origin request policy: CloudFront does not allow it there.
  - Separate GET-only and POST behaviours: CloudFront matches on path, not method.
- **Tested:** the function's rewrite rules with Node against every kind of address; `terraform validate` for staging and the emulator; the build's paths (`/app/assets/…`, the font under `/app/assets/`). The emulator checks run in this pull request's CI. **Not provable on the emulator:** real CloudFront forwarding to the Lambda, and Google sign-in under the CSP. Staging's smoke test and the owner's sign-in on staging and prod (OPEN-ITEMS) cover those.

## 117. Google's button stylesheet is loaded with the page (2026-11-21)

- **Decision:** `index.html` links `https://accounts.google.com/gsi/style` before the GIS script. The button's container is fixed at 44 px high with `overflow: hidden`, and any SVG in it is held to icon size.
- **Why:** On staging, the sign-in page showed a page-wide Google "G" for about a second. GIS inserts fallback markup (with that logo as an unsized SVG) when it draws the button, then fetches its own stylesheet, which hides the fallback. Loading the stylesheet up front removes the gap. The size limits are a second guard if the stylesheet is slow or blocked. The CSP already allows that URL (decision 116).
- **Rejected:** hiding the button until Google's iframe appears (it relies on GIS's internal markup, which Google can change).

## 118. Both Lambdas export to Grafana Cloud directly; the token is read from SSM before telemetry starts (2026-11-21)

- **Decision:**
  - **Reaching Grafana Cloud.** The API and the reminder function send traces, metrics and logs straight to the stack's OTLP gateway (decision 64). The module input `grafana` (endpoint and instance id, neither secret) sets `OTEL_EXPORTER_OTLP_ENDPOINT`, `DINOGYM_OTLP_INSTANCE_ID` and `DINOGYM_OTLP_TOKEN_PARAM`; while it is `null`, telemetry stays `none`. At cold start each binary reads `/dinogym-<env>/grafana-token` from SSM, and builds `Authorization: Basic base64(instance id:token)` before it sets up telemetry.
  - **One SSM call.** A new package, `internal/secrets`, reads every parameter a binary needs in a single `GetParameters`. For the API that is the session key and the token. A missing parameter stops the start and is named in the error, without the value.
  - **The reminder function's role** may now read the token and decrypt it through SSM only. The bootstrap's boundary already allowed `grafana-token` for every role in the environment (decision 71), so `infra/bootstrap` needs no hand-applied change.
  - **One metric series per Lambda instance.** Each process sets `service.instance.id` to a random id. SDK 1.47 sets none by default. Cumulative counters from two warm instances would otherwise land on one series and overwrite each other.
  - **Fewer series.** otelhttp's request and response body-size histograms are dropped with a view. Request duration, which the dashboards use, is kept.
- **Why:**
  - Reading the token in code keeps it out of Terraform state and out of the Lambda's configuration, where anyone with `lambda:GetFunctionConfiguration` could read it (decision 69).
  - One call keeps cold start to the single SSM round trip it already had.
  - The instance id makes the metrics correct; dropping the body sizes keeps the free tier's 10,000-series limit far away.
  - Per-instance series churn is small at one user's traffic: a handful of cold starts a day.
- **Rejected:**
  - Putting the token in `OTEL_EXPORTER_OTLP_HEADERS` from Terraform: it would sit in state and in the function's configuration.
  - The AWS Parameters and Secrets Lambda extension: another layer and process for one read at start.
  - Secrets Manager: $0.40 a secret a month, and nothing to rotate automatically.
  - Storing the whole Basic header in SSM: it would mix the non-secret instance id into the secret, and the value would no longer be the token Grafana shows.
  - Delta temporality instead of an instance id: Grafana Cloud's OTLP ingestion is built for cumulative metrics.
- **Cost:** No AWS charge beyond a few kilobytes of data transfer out per request, inside the free 100 GB. Every request now waits for the flush to Grafana's gateway (decision 64's trade-off). How long that takes is checked on staging (OPEN-ITEMS).
- **Tested:**
  - Go unit tests: the settings (both or neither, `otlp` needs an endpoint), the Basic header reaching `/v1/traces`, `/v1/metrics` and `/v1/logs`, `OTEL_EXPORTER_OTLP_HEADERS` kept when no token is configured, distinct instance ids, the dropped histograms, and `secrets.Read` against a fake SSM.
  - On the emulator: a SecureString written as the owner writes it is read back decrypted, and a missing one is named. This proves the API usage only; the emulator evaluates neither IAM nor KMS.
  - `terraform validate` for the emulator, staging and prod roots, with `grafana` both unset and set.
  - **Not tested here:** that Grafana Cloud accepts the data, and the reminder role's access on real AWS. Staging shows both.

## 119. The API flushes telemetry after its response is sent, through an internal Lambda extension (2026-11-21, changes decision 64)

- **Decision:**
  - At cold start, the API registers itself as an internal Lambda extension for `INVOKE` events (`internal/postinvoke`). The handler signals when it is done. The extension's loop then runs the telemetry flush, with at most 3 seconds and never past the invocation's deadline, and only after that asks Lambda for the next event.
  - If registration fails, the API logs a warning and flushes before responding, as before.
  - `Telemetry.Flush` now runs the trace, metric and log exports at the same time.
  - The reminder function keeps the plain flush: Scheduler invokes it asynchronously, so nobody waits on it.
- **Why:**
  - The first staging deploy with Grafana Cloud (decision 118) answered `/health` in about 1.4 s, against 0.33 s for prod without telemetry. Grafana's traces showed the handler itself taking about 0.1 ms. The rest was three OTLP exports in a row before the response.
  - Lambda returns the function's response to the client as soon as the runtime posts it, and freezes the environment only once every extension has asked for its next event (AWS Lambda Extensions API). That keeps decision 64's guarantee, that nothing is left unsent when the process freezes, without the client waiting.
- **Rejected:**
  - Flushing in a goroutine without an extension: Lambda may freeze the process mid-export, and the data would be lost or sent late.
  - The ADOT or OpenTelemetry collector Lambda layer: rejected in decision 64, for a separate process and more cold-start time.
  - Flushing only every N seconds: some requests would still pay the full cost, and data would wait in a frozen process.
  - Sampling or turning off a signal: it hides the problem without fixing it.
- **Cost:** The flush still counts toward each invocation's billed duration; CloudWatch reports it as `PostRuntimeExtensionsDuration`. At one user's traffic that stays inside Lambda's free tier. A request arriving while the previous flush runs waits for it, since one environment handles one invocation at a time.
- **Tested:** Unit tests against a fake Extensions API check the order register → next → (handler done) → work → next. They also check the work's deadline (the limit, or the invocation's deadline if sooner), the inline fallback once the loop stops, and that a failed registration is an error. They ran three times with `-race`, and the arm64 Lambda binary builds. **Not tested here:** that real Lambda accepts the registration and returns the response before the flush. The staging latency check in OPEN-ITEMS covers it.
