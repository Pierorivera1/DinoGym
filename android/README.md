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

While signed in, the app syncs in the background with WorkManager: SQLite triggers mark every changed row in `sync_pending`, and `data/sync/SyncEngine` pushes those rows and pulls newer ones (decisions 88–91). The first sign-in uploads the whole history. Settings › ACCOUNT shows the sync state and SYNC NOW. Debug builds sync with staging, release builds with prod.

Shared routine links (task 23) follow the same split: debug builds open and import `https://d1q286cdeyw8ks.cloudfront.net/r/<id>` (staging), release builds `https://d1ktti3qymlgmy.cloudfront.net/r/<id>` (prod). The hosts are `shareHost` in `app/build.gradle.kts`; App Links verify only for builds signed with a key listed in that environment's `assetlinks.json` (decision 99).

| Build | API | Can sign in |
|---|---|---|
| Debug, built on the laptop | staging, `https://ypn4asfnil.execute-api.us-east-1.amazonaws.com` | Yes, when the laptop's debug key SHA-1 is registered for the Android OAuth client |

This laptop has two debug keys, and both are registered as Android OAuth clients for `io.github.pierorivera1.dinogym.debug` (decision 85):

| Built by | Keystore | SHA-1 |
|---|---|---|
| Android Studio | `~/.config/.android/debug.keystore` | `CE:9E:E3:48:E8:31:92:C7:34:7E:AC:39:FC:91:A3:55:61:1E:44:F4` |
| `./gradlew` in a terminal | `~/.android/debug.keystore` | `BE:99:9E:4C:0B:49:7F:B3:28:F7:B8:AA:DB:7C:1E:FC:99:58:37:35` |

If the Google account sheet closes and the button comes back with no message, the build's SHA-1 is not registered: Credential Manager reports it as a cancel. Check the installed APK with `apksigner verify --print-certs` and add its SHA-1 as another Android client.
| Debug, built in CI | staging | No: CI uses a throwaway debug key whose SHA-1 is not registered, so Google refuses the request |
| Release | prod, `https://cm884h7had.execute-api.us-east-1.amazonaws.com` | Yes (the release key's SHA-1 is registered) |

The URLs are `BuildConfig.API_BASE_URL` in `app/build.gradle.kts`. They are API Gateway's generated URLs, so they change if an HTTP API is ever recreated; update them there (later: a custom domain, or reading the Terraform output). The Web OAuth client ID in `BuildConfig.GOOGLE_SERVER_CLIENT_ID` is not secret (decision 80).
