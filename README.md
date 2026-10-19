# DinoGym

DinoGym is a native Android workout tracker for recording bodyweight training and reviewing progress. It is designed for quick logging during a workout, with local storage so logging and history work offline.

## Screenshots

| Training | Workout logging | Progress | Settings | Tablet layout |
| --- | --- | --- | --- | --- |
| ![Training screen](docs/screenshots/train.png) | ![Workout screen](docs/screenshots/workout.png) | ![Progress screen](docs/screenshots/progress.png) | ![Theme settings in light mode](docs/screenshots/settings.png) | ![Tablet layout](docs/screenshots/tablet.png) |

## Download

[Download the latest APK](https://github.com/Pierorivera1/DinoGym/releases/latest/download/app-debug.apk) from GitHub Releases. This is a debug build for direct installation; Android may ask you to allow installs from your browser.

## Features

- **Browse your plan:** swipe between the compact day overview and exercise details, or tap their tabs. Tap a selected day again to open its details, select a workout before starting it, or edit it from its day menu. Green accents and warm white surfaces define light mode.
- **Plan training:** create named workouts and rotating training splits, reorder their exercises/workouts, and choose the split to follow. The home screen shows the next workout and advances after it is completed.
- **Log workouts:** open the compact exercise overview, edit individual sets with reps (or a custom metric), optional kilograms, then mark sets logged. Only logged sets are saved. Optionally update workout targets when finishing.
- **Share exercises:** maintain one exercise library and add the same exercise to multiple workouts with independent targets. Create exercises with a display template containing exactly one `{n}` placeholder and a metric unit. For example, `3s + {n} pulls + 3s` keeps the holds fixed while tracking pull repetitions.
- **Manage exercises:** create exercises directly from the Add exercise picker, edit exercises, archive them after confirmation, and restore them from the Archived list. Archived exercises stay out of new workouts; saved history keeps its original exercise details.
- **Manage workouts:** expand sessions in workout history, correct their values and sets, or delete a session after confirmation. Editing preserves the session's ID and date.
- **Review progress:** compare a workout with the previous session, expand saved workout history, and open exercises within each workout and compare weekly best-set values in a connected-dot line chart across the full saved history. Weeks start on Monday; missing weeks are not treated as zero.
- **Stay focused:** focus the next set’s input after logging, show the exercise’s previous results, and automatically move to the next exercise after its last set. Adjust an active or paused rest by 15 seconds, and filter workout history with the training calendar.
- **Resume and rest:** active session drafts survive app restarts. Logging an intermediate set starts its configurable rest timer; the last set skips rest. Pause, resume, restart, or skip it. A quiet ongoing notification supports background rest, with a two-second sound-only completion alert and no vibration.
- **Use it offline:** workouts and custom exercises are stored on the device and remain available after restarting the app.
- **Choose your theme:** open Settings at the top right of Exercises and toggle Dark mode. Your choice is saved between launches; the app follows the system appearance until you choose a mode. Wider layouts show a navigation rail.

## Phone requirements

- Android phone running Android 8.0 (API 26) or newer.
- Internet access is only needed to download the APK. Workouts and custom exercises are stored on the phone and work offline.

The app targets Android 16 (API 36) and supports Android 8.0 (API 26) and newer. You do not need a computer or Android Studio to install or use the released APK. Download it from [GitHub Releases](https://github.com/Pierorivera1/DinoGym/releases/latest/download/app-debug.apk), open the downloaded APK on your phone, and allow installation from that source if Android asks. This release is a debug APK; Android may show a confirmation before installing it.

## Optional: open and run the project in Android Studio

Android Studio is only needed if you want to build or modify the app from source, or run it from the IDE. Open the repository root (`DinoGym`) as a Gradle project, let Gradle sync, then select the `app` configuration and an Android 8.0+ phone or emulator and press **Run**. For this development workflow, install Android SDK Platform 36 when prompted and use JDK 17 (Android Studio's bundled Gradle JDK is suitable). An internet connection is needed for the initial Gradle sync/build because Gradle downloads the project dependencies and plugins.

Compose previews are in `app/src/main/java/com/dinogym/ui/main/Previews.kt`. Open that file and use the editor's Split or Design view to inspect the sample light, dark, workout, progress, exercise, and wider-screen layouts. Preview data is separate from installed app data.

## Build from the command line

From the repository root, build the debug APK:

```bash
./gradlew :app:assembleDebug
```

The APK is created at `app/build/outputs/apk/debug/app-debug.apk`. Install it on a connected device with:

```bash
./gradlew :app:installDebug
```

Run the local checks with:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Instrumented tests require a running emulator or connected Android device:

```bash
./gradlew :app:connectedDebugAndroidTest
```

The Gradle wrapper is included, so a separate Gradle installation is not required. On Windows, use `gradlew.bat` in place of `./gradlew`.

## Project structure

```text
.
├── app/
│   └── src/
│       ├── main/java/com/dinogym/
│       │   ├── data/           # Workout models and local JSON repository
│       │   ├── theme/          # Compose theme and typography
│       │   ├── ui/main/        # Training, workout, progress, and previews
│       │   ├── MainActivity.kt
│       │   └── Navigation.kt
│       ├── test/               # Local unit tests
│       └── androidTest/         # Device and UI tests
├── docs/screenshots/           # App screenshots used in this README
├── gradle/                     # Wrapper and version catalog
├── build.gradle.kts            # Root Gradle plugins
└── settings.gradle.kts         # Gradle project and repositories
```

The app uses Kotlin, Jetpack Compose with Material 3, AndroidX lifecycle and Navigation 3, Kotlin serialization, and Gradle Kotlin DSL. Dependency and plugin versions are centralized in `gradle/libs.versions.toml`.

## Data and privacy

Workout data is stored locally in the app's private files directory as `workouts.json`; the active draft is stored separately as `active-workout.json`. Version 2 stores shared exercises, workout templates, splits, and individual set records. Legacy files are decoded without being rewritten until a normal save; that first save retains the original in `workouts-v1.backup.json`. Known matching starter duplicates are consolidated, while customized variants and historical display details are preserved. Writes use Android's `AtomicFile` support to reduce the chance of a partial write. If stored data cannot be read, the app reports the problem and preserves the file instead of replacing it with empty history. The app does not currently include accounts, cloud synchronization, export/import, or a supported backup-and-restore flow. Uninstalling the app or clearing its storage removes local workouts and custom exercises; export important data before doing so once an export feature is available.

There is no network requirement during workout logging. Gradle does download build dependencies when syncing or building the project.

## Current scope and limitations

- Weight is optional and uses kilograms. Splits rotate by completion rather than weekdays.
- Rest sounds respect device audio policy; force-stopping the app stops the timer. Background sound should also be verified on the target physical device. The Android 14+ foreground service declares its workout-timer special use; distribution through Google Play requires the corresponding foreground-service declaration/review.
- Charts show the best recorded set value per session across all workouts for an exercise; history includes every logged set and optional weight.
- Persistence currently uses a single local JSON snapshot. Room/SQLite migrations, cloud sync, and multi-device conflict handling are future work.
- The release build is not configured for minification or production signing. Keep signing keys and credentials outside version control when adding a release workflow.

## Contributing

Keep changes focused and consistent with the existing feature and data organization. Before opening a change, run the unit tests and Android Lint commands above; run instrumented tests when changing UI flows or device-specific behavior. Do not commit machine-specific `local.properties`, build output, IDE state, signing keys, or credentials.

## License

No license has been added to this repository yet. All rights remain with the copyright holder until a license is provided.
