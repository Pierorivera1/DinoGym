# Architecture and build order

Written 2026-11-16 (decisions 34–37). This covers the first daily-usable version (decision 31), which runs fully offline. The cloud side gets its own section here once its open items in [OPEN-ITEMS.md](OPEN-ITEMS.md) are decided.

## Android app

| Setting | Value |
|---|---|
| Application ID and namespace | `io.github.pierorivera1.dinogym` (decision 34). Version 1 is `com.dinogym`, so both install side by side |
| Debug build | `applicationIdSuffix = ".debug"`, so a debug build and a release build can also coexist |
| minSdk | 26 (same as version 1) |
| compileSdk / targetSdk | 36 (installed on the laptop) |
| Modules | One: `:app` (decision 35) |
| Versions | Gradle version catalog `android/gradle/libs.versions.toml`, latest stable at scaffold time, pinned |
| Gradle limits | [android/gradle.properties](../android/gradle.properties). Do not raise them |

### Layers

The app is one Gradle module with three layers, kept apart by package:

```
io.github.pierorivera1.dinogym
├── domain/     pure Kotlin: models and rules (cycle, pre-fill, set-1 bests). No Android imports
├── data/       Room database, DAOs, repositories, first-run seed data
├── timer/      rest timer: background notification and service
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
| `session` | routineId, programId, startedAt, endedAt (null = in progress) |
| `set_entry` | sessionId, exerciseId, setIndex, reps, weightKg (nullable), note |

- A workout in progress is a `session` with no `endedAt`. Sets are written as they are logged, so a workout survives the app being killed, as in version 1.
- Completing, skipping or marking a rest day done moves `program.currentStep` on (decision 29).
- Progress is not stored. It is a query over set 1 of each finished session, keeping the days set 1 beat the previous best (decision 28).
- The owner's program (WISHLIST.md) is inserted on first run.

### Testing

- Local: only the JVM unit tests of the class that changed (`./gradlew :app:testDebugUnitTest --tests '<Class>'`).
- DAO and UI tests are `androidTest` and run only in GitHub Actions on an emulator.
- CI on every push to `main`: build, unit tests, lint. Instrumented tests are added in step 3.

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

These are open in [OPEN-ITEMS.md](OPEN-ITEMS.md) and block sign-in and sync, not the steps above:

- The sync design, the DynamoDB data model, the AWS region and where secrets live.
- The AWS Emulator, which needs Docker running. Docker needs `sudo` once to start the service and add the user to the `docker` group; the agent asks for the password at that point.
