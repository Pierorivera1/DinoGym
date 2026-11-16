# DinoGym Android app

One Gradle module, `:app`. Structure and build order are in [docs/ARCHITECTURE.md](../docs/ARCHITECTURE.md).

## Build

Needs the Android SDK (`ANDROID_HOME`, or `sdk.dir` in `local.properties`). Gradle downloads its own JDK 21.

```bash
./gradlew :app:assembleDebug        # debug APK in app/build/outputs/apk/debug/
./gradlew :app:testDebugUnitTest    # JVM unit tests
./gradlew :app:lintDebug            # Android lint
```

On the laptop, run one test class at a time (`--tests '<Class>'`) and never the instrumented tests; those run in GitHub Actions. The limits in `gradle.properties` keep the CPU cool and must not be raised.
