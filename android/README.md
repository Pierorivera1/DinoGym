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

## Sign-in (task 20)

Sign-in is optional and lives only in Settings › ACCOUNT; nothing else needs an account. The app gets a Google ID token from Credential Manager, exchanges it at `/auth/google` for a DinoGym session, and keeps that session encrypted with a key in the Android Keystore (decisions 81–83). The code is in `app/src/main/kotlin/.../data/account/`.

| Build | API | Can sign in |
|---|---|---|
| Debug, built on the laptop | staging, `https://ypn4asfnil.execute-api.us-east-1.amazonaws.com` | Yes, when the laptop's debug key SHA-1 is registered for the Android OAuth client |
| Debug, built in CI | staging | No: CI uses a throwaway debug key whose SHA-1 is not registered, so Google refuses the request |
| Release | prod, `https://cm884h7had.execute-api.us-east-1.amazonaws.com` | Yes (the release key's SHA-1 is registered) |

The URLs are `BuildConfig.API_BASE_URL` in `app/build.gradle.kts`. They are API Gateway's generated URLs, so they change if an HTTP API is ever recreated; update them there (later: a custom domain, or reading the Terraform output). The Web OAuth client ID in `BuildConfig.GOOGLE_SERVER_CLIENT_ID` is not secret (decision 80).
