# Releasing the app

The app ships as a signed APK on GitHub Releases (decision 8). The release workflow builds and publishes it when a version tag is pushed.

## Release a version

1. On `main`, raise `versionCode` by one and set `versionName` in [android/app/build.gradle.kts](../android/app/build.gradle.kts). Merge that change through a pull request.
2. Tag the merge commit with `v` + `versionName` and push the tag:
   ```bash
   git tag v2.0.0
   git push origin v2.0.0
   ```
3. [.github/workflows/release.yml](../.github/workflows/release.yml) checks that the tag matches `versionName`, builds `assembleRelease` with the keystore from the repository secrets, verifies the signature with `apksigner`, and creates the release with `DinoGym-<version>.apk` and its SHA-256.
4. On the phone, open the release page and install the APK. Android keeps the app's data across updates only when the new APK is signed with the same key.

## The signing key

- The keystore is `~/.config/dinogym/release.jks` (PKCS12, alias `dinogym`, RSA 4096, valid about 27 years). Its passwords are in `~/.config/dinogym/keystore.properties`. Both are outside the repository and readable only by the owner.
- **Back both files up somewhere safe (a password manager).** If the key is lost, no update can be installed over the existing app; the only way out is uninstalling, which deletes the data on the phone.
- The repository secrets hold the same key for the workflow: `DINOGYM_KEYSTORE_BASE64`, `DINOGYM_KEYSTORE_PASSWORD`, `DINOGYM_KEY_ALIAS`, `DINOGYM_KEY_PASSWORD`. The workflow writes the keystore to the runner's temporary folder and deletes it at the end of the job.
- SHA-256 fingerprint of the signing certificate: `2C:A1:4F:19:D6:CE:3E:85:8A:E2:B1:43:99:D1:67:28:E3:58:B6:4B:25:41:A2:C4:9B:64:E4:79:00:0D:6D:84`. Firebase asks for it when the Android app is added to the project.

## Building a signed APK locally

`android/keystore.properties` (git-ignored) is a symlink to `~/.config/dinogym/keystore.properties`. With it in place:

```bash
cd android
./gradlew :app:assembleRelease
```

Without it, or without the `DINOGYM_*` environment variables, the release build is unsigned. That is how CI builds it on every push.
