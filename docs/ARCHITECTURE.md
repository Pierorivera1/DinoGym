# Architecture and build order

Written 2026-11-16 (decisions 34–37). This covers the first daily-usable version (decision 31), which runs fully offline. The cloud side gets its own section here once its open items in [OPEN-ITEMS.md](OPEN-ITEMS.md) are decided.

## Android app

| Setting | Value |
|---|---|
| Application ID and namespace | `io.github.pierorivera1.dinogym` (decision 34). Version 1 is `com.dinogym`, so both install side by side |
| Debug build | `applicationIdSuffix = ".debug"`, so a debug build and a release build can also coexist |
| minSdk | 26 (same as version 1) |
| compileSdk / targetSdk | 37 / 36. The current AndroidX libraries need compileSdk 37 (decision 38) |
| Modules | `:app` (decision 35), plus `:api-client`, the generated API client (decision 60) |
| Versions | Gradle version catalog `android/gradle/libs.versions.toml`, latest stable at scaffold time, pinned |
| JDK for Gradle | 21, pinned in `android/gradle/gradle-daemon-jvm.properties`; Gradle downloads it if missing (decision 38) |
| Gradle limits | [android/gradle.properties](../android/gradle.properties). Do not raise them |

### Layers

The app is one Gradle module with three layers, kept apart by package:

```
io.github.pierorivera1.dinogym
├── domain/     pure Kotlin: models and rules (cycle, pre-fill, set-1 bests). No Android imports
├── data/       Room database, DAOs, repositories, first-run seed data;
│               account/: sign-in, the encrypted session and token refresh (task 20)
│               sync/: change tracking, push and pull, WorkManager scheduling (task 21)
├── timer/      rest timer: end time, countdown notification and alarm (decision 42)
└── ui/
    ├── theme/      DinoTheme tokens, fonts, shapes (from DESIGN.md)
    ├── components/ buttons, text box, stat bar, stepper, cursor, bottom bar, icons
    └── <feature>/  home, workout, programs, exercises, stats, settings
```

- Data flows one way: Room → repository (`Flow`) → `ViewModel` (`StateFlow` of a screen state) → composable. Events go back as function calls on the `ViewModel`.
- Hilt provides the database, DAOs, repositories and a `Clock`, so tests can control time.
- `domain/` has no Android dependency, so its rules are tested with plain JVM unit tests.

### Data model (Room)

Every table uses a text UUID primary key plus `updatedAt` and a nullable `deletedAt` (soft delete), so the future sync design starts from rows that can be merged (decision 36).

| Table | Columns (besides id, updatedAt, deletedAt) |
|---|---|
| `program` | name, type (`GYM`/`CALISTHENICS`), currentStep, isActive |
| `cycle_step` | programId, position, routineId (null = rest day) |
| `routine` | name |
| `exercise` | name, iconKey, kind (`REPS`, `TEMPLATE`, `SHOW_ONLY`), template (for example `3s + {n} pulls + 3s`), weightEnabled, archived |
| `routine_exercise` | routineId, exerciseId, position, sets, repMin, repMax, restSeconds |
| `session` | routineId, programId, startedAt, endedAt (null = in progress), currentPosition (the exercise a workout in progress is on) |
| `set_entry` | sessionId, exerciseId, setIndex, reps, weightKg (nullable), note |
| `weigh_in` | measuredAt, weightKg (database version 2, decision 49) |
| `preference` | key (primary key instead of a UUID), value. Synced user settings such as the goal weight |

- `local_flag` is one extra, local-only table (key, value). It records that the first-run seed has run (decision 40) and, since task 21, the sync cursor, the account the phone last synced with and the last sync result. It is never synced.
- `sync_pending` (seq, tableName, rowId) and `sync_inbox` (tableName, rowId, json) are local-only sync tables (database version 3, decisions 88–89). Triggers on the nine synced tables fill `sync_pending`.
- A workout in progress is a `session` with no `endedAt`. Sets are written as they are logged, so a workout survives the app being killed, as in version 1.
- Completing, skipping or marking a rest day done moves `program.currentStep` on (decision 29).
- Progress is not stored. It is a query over set 1 of each finished session, keeping the days set 1 beat the previous best (decision 28).
- The owner's program (WISHLIST.md) is inserted on first run.
- Programs from outside the user's own edits (the first-run seed, the starter programs of task 22, and imported shares) are plain `ProgramTemplate` data copied in by `data/repository/ProgramInstaller.kt`: new ids for every row, library exercises reused by name and kind, taken names numbered (decision 93). The starter content is in `data/seed/StarterPrograms.kt` (decision 94).

### Sync (task 21)

Decisions 51, 62, 86–91. Offline first: no screen waits on sync, and everything works signed out.

1. **Tracking.** Every insert or update of a synced row fires a trigger that writes `(table, id)` with a new `seq` into `sync_pending`. Theme (DataStore) and `local_flag` never sync.
2. **When.** `SyncController` follows the signed-in user. Signed in: a periodic run every 6 hours, a run 60 s after any change, and one right after sign-in and on SYNC NOW (WorkManager unique works, network required). Signed out: all cancelled; pending marks stay.
3. **Account.** The first sign-in, or a different account than last time, marks every row pending and resets the cursor to 0, so the whole history goes up.
4. **Push.** Pending rows, parents first, in batches of ≤ 500 rows and ≈ 900 KB. `accepted`/`stale` clear the entry if it was not changed again since; `rejected` stays for a later run.
5. **Pull.** Pages of 1000 from the stored cursor until `hasMore` is false. Each page and its cursor commit in one transaction; rows apply parents first by the merge rule (newer `updatedAt` wins, deletion wins a tie); a row whose parent has not arrived waits in `sync_inbox`.
6. **Errors.** 401 → refresh once and retry once; offline, 429, 5xx → retry later with backoff; 400 or a second 401 → "Sync failed" until a later run works.

Code: `data/sync/` (engine, rules, Room store, scheduling), `data/local/SyncTracking.kt` and `SyncDao.kt`, the sync row in `ui/settings/AccountSection.kt`.

### Shareable routines (task 23)

Decisions 96–101. A share is an immutable snapshot of a routine or a program; links look like `https://<distribution>.cloudfront.net/r/<id>`.

```
phone ──POST /shares (signed in)──▶ API Gateway ─▶ Lambda ─▶ DynamoDB  SHARE#<id>
browser ──/r/<id>──▶ CloudFront ─┬─ /r/*, /web/*, /.well-known/* ─▶ S3 (private, OAC)
                                 └─ GET /shares/* ─▶ API Gateway ─▶ Lambda
phone (import) ──GET /shares/<id>──▶ API Gateway (its own environment's API)
```

1. **Publish.** The share action (routine and program editors) builds the snapshot from Room and posts it; the API answers the id and the link (`url`, from the distribution's address that Terraform gives the Lambda). Needs sign-in; 20 shares a user a day; 64 KiB at most.
2. **Read.** Public. The page (`web/share/`) and the app read the same `GET /shares/{id}`; the answer is cached an hour by CloudFront.
3. **Open.** A `/r/*` link (an App Link once the exact hosts are in the manifest, decision 99), the page's `intent://` button, or a link shared or pasted as text opens the import preview.
4. **Import.** The snapshot becomes a `ProgramTemplate` and goes through `ProgramInstaller` (decision 93): new ids, exercises matched by name and kind, names numbered, active only when no program is. The new rows then sync like any others.

Code: `backend/internal/server/shares.go`, `backend/internal/store/shares.go`, `web/share/`, `infra/modules/dinogym/web.tf`, and on the phone `data/share/` and `ui/share/`.

### Testing

- Local: only the JVM unit tests of the class that changed (`./gradlew :app:testDebugUnitTest --tests '<Class>'`).
- DAO and UI tests are `androidTest` and run only in GitHub Actions on an emulator.
- CI ([.github/workflows/android.yml](../.github/workflows/android.yml)) on every push that touches `android/`: build, unit tests, lint. Instrumented tests are added with the database (task 4).

## Build order

The full numbered list, including the cloud work, with checkboxes is in [TASKS.md](TASKS.md). This section adds detail for the app tasks.

Each step ends with the app building and its tests passing. Screens with an approved mock (DESIGN.md) are built first. The other mocks are made and approved while those steps run.

| # | Step | Needs |
|---|---|---|
| 1 | **Scaffold.** Gradle wrapper, version catalog, `:app` with Compose, Navigation 3, Hilt and Room wired in, `gradle.properties` limits, empty app that launches. GitHub Actions workflow: build, unit tests, lint | – |
| 2 | **Theme and components.** Colour tokens, bundled fonts, stepped shape, raised modifier, buttons, text box, stat bar, stepper, cursor, bottom bar, first icons as vector drawables, English and Spanish string files | DESIGN.md |
| 3 | **Data and rules.** Room entities and DAOs, repositories, first-run seed of the owner's program, domain rules (cycle advance, pre-fill from last session, set-1 bests) with unit tests; DAO tests in CI | – |
| 4 | **Workout flow.** Home, active exercise, rest picker, rest timer running in the background, summary. Resume a workout after a restart | Approved mocks |
| – | **In parallel: second mock batch.** Programs, Exercises, Stats, Settings, for the owner's approval | Owner |
| 5 | **Programs.** Program list, cycle editor, routine editor, exercise picker | Step 4 mocks approved |
| 6 | **Exercises.** Library, add and edit (icon, template, weight switch), archive and restore | Approved mock |
| 7 | **Stats.** History by date, edit a past session, progress view per exercise | Approved mock |
| 8 | **Settings and release.** Theme setting, Spanish review of every string, release signing (keystore kept out of git), signed APK on GitHub Releases. The owner switches from version 1 here | – |

After step 8, in the order of decision 31: body weight, sign-in and sync, starter programs and program builder, shareable routines, reminders, web dashboard.

### Before the cloud work starts

The cloud items are decided: sync (decision 51), the DynamoDB model (52), the region (53), secrets (54) and FCM auth (55). Docker and the AWS Emulator are tasks 13 and 14.
