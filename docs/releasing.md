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
`JUICE_KEYSTORE_PASSWORD`, `JUICE_KEY_ALIAS` and `JUICE_KEY_PASSWORD`. Release builds never fall back to
`app/debug.keystore`: when `JUICE_KEYSTORE_FILE` is not set, `./gradlew :app:assembleRelease` fails with an error
naming it. Tests, lint and coverage do not need it. The release workflow fails the same way when
`JUICE_KEYSTORE_BASE64` is not set, before building, so a debug-signed APK is never published.
Release builds are minified with R8 (`app/proguard-rules.pro`).

## Changing the signing key

Android only installs an update when it is signed with the same key as the installed app. If the key
changes (including going from the debug fallback to a real key), testers must uninstall Juice first, which
deletes its data. Create the key once and keep it.

## Owner note: rotate the Groq key

Earlier release and CI APKs were built with the Groq API key compiled in. Rotate that key at
console.groq.com and replace or delete the old Release APKs on GitHub (manual step, not done by the build).
