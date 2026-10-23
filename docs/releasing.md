# Releasing Juice

Pushing a version tag builds a signed APK and publishes it on the repository's GitHub Releases page
(`.github/workflows/release.yml`).

## Cut a release

1. In `app/build.gradle.kts`, bump `versionName` (e.g. `"0.2"`) and increase `versionCode` by one.
2. Merge that change to `main`.
3. Tag and push, with the tag equal to `v` plus `versionName`:
   ```
   git tag v0.2
   git push origin v0.2
   ```

The workflow fails before publishing if the tag (minus the `v`) differs from `versionName`. On success
the release `v0.2` appears with generated notes and `juice-v0.2.apk` attached.

## Create the signing key (once)

```
keytool -genkeypair -v -keystore juice-release.jks -alias juice \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 juice-release.jks   # macOS: base64 -i juice-release.jks
```

Add these repository secrets (Settings → Secrets and variables → Actions):

| Secret | Value |
| --- | --- |
| `JUICE_KEYSTORE_BASE64` | the base64 output above |
| `JUICE_KEYSTORE_PASSWORD` | the keystore password |
| `JUICE_KEY_ALIAS` | the alias (`juice` above) |
| `JUICE_KEY_PASSWORD` | the key password |

Keep `juice-release.jks` and its passwords somewhere safe outside the repository.

Locally, the build reads the keystore path and passwords from `JUICE_KEYSTORE_FILE`,
`JUICE_KEYSTORE_PASSWORD`, `JUICE_KEY_ALIAS` and `JUICE_KEY_PASSWORD`. When they are absent it signs with
`app/debug.keystore`, so `./gradlew :app:assembleRelease` still works. The workflow logs a warning when
`JUICE_KEYSTORE_BASE64` is not set.

## Changing the signing key

Android only installs an update when it is signed with the same key as the installed app. If the key
changes (including going from the debug fallback to a real key), testers must uninstall Juice first, which
deletes its data. Create the key once and keep it.
